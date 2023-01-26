package xyz.wagyourtail.unimined.mappings

import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.*
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.MappingsProvider
import xyz.wagyourtail.unimined.api.mappings.MemoryMapping
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.api.unimined
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

abstract class MappingsProviderImpl(
    val project: Project
) : MappingsProvider() {

    private val mcProvider = project.extensions.getByType(MinecraftProvider::class.java)

    init {
        for (envType in EnvType.values()) {
            getMappings(envType)
        }
        project.tasks.register("exportMappings", MappingExportTaskImpl::class.java) {
            it.group = "unimined"
            it.description = "Exports mappings to various formats."
        }
    }

    private fun getOfficialMappings(): MemoryMappingTree {
        val off = project.configurations.detachedConfiguration(
            project.dependencies.create(
                "net.minecraft:minecraft:${mcProvider.minecraft.version}:client-mappings"
            )
        )
        val file = off.resolve().first { it.extension == "txt" }
        val tree = MemoryMappingTree()
        file.inputStream()
            .use { ProGuardReader.read(it.reader(), "mojmap", "official",
                MappingSourceNsSwitch(tree, "official")
            ) }
        return tree
    }

    @ApiStatus.Internal
    final override fun getMappings(envType: EnvType): Configuration {
        return project.configurations.maybeCreate(
            Constants.MAPPINGS_PROVIDER + (envType.classifier?.capitalized() ?: "")
        )
    }

    @ApiStatus.Internal
    override fun hasStubs(envType: EnvType): Boolean {
        return stubs.contains(envType)
    }

    private val stubs = mutableMapOf<EnvType, MemoryMapping>()

    override fun getStub(envType: EnvType): MemoryMapping {
        return stubs.computeIfAbsent(envType) {
            MemoryMapping()
        }
    }

    private fun getInternalMappingsConfig(envType: EnvType): Configuration {
        return project.configurations.maybeCreate(
            Constants.MAPPINGS_INTERNAL + (envType.classifier?.capitalized() ?: "")
        )
    }

    private val mappingTrees = mutableMapOf<EnvType, MappingTreeView>()

    @ApiStatus.Internal
    override fun getMappingTree(envType: EnvType): MappingTreeView {
        return mappingTrees.computeIfAbsent(envType, ::resolveMappingTree)
    }

    private val combinedNamesMap = mutableMapOf<EnvType, String>()

    @ApiStatus.Internal
    override fun getCombinedNames(envType: EnvType): String {
        return combinedNamesMap.computeIfAbsent(envType) { env ->
            val thisEnv = getMappings(envType).dependencies.toMutableSet()
            if (envType != EnvType.COMBINED) {
                thisEnv.addAll(getMappings(EnvType.COMBINED).dependencies ?: setOf())
            }
            val mapping = thisEnv.sortedBy { "${it.name}-${it.version}" }
                .map { it.name + "-" + it.version } + if (stubs.contains(envType)) listOf("stub-${getStub(env).hash}") else listOf()

            mapping.joinToString("-")
        }
    }

    private val mappingFileEnvs = mutableMapOf<EnvType, Set<Pair<Dependency, File>>>()

    @Synchronized
    private fun mappingsFiles(envType: EnvType): Set<Pair<Dependency, File>> {
        return if (envType != EnvType.COMBINED) {
            mappingsFiles(EnvType.COMBINED)
        } else {
            setOf()
        } + mappingFileEnvs.computeIfAbsent(envType) {
            val config = getMappings(envType)
            config.dependencies.map {
                it to config.files(it).first { it.extension != "pom" }
            }.toSet()
        }
    }

    private fun writeToCache(file: Path, mappingTree: MappingTreeView) {
        file.parent.createDirectories()
//        val filtered = MemoryMappingTree()
//        mappingTree.accept(
//            MappingDstNsFilter(
//                filtered,
//                listOf("intermediary", "searge", "named").filter { mappingTree.dstNamespaces.contains(it) })
//        )
        ZipOutputStream(file.outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)).use {
            it.putNextEntry(ZipEntry("mappings/mappings.tiny"))
            OutputStreamWriter(it).let { writer ->
                mappingTree.accept(Tiny2Writer2(writer, false))
                writer.flush()
            }
            it.closeEntry()
        }
    }

    @ApiStatus.Internal
    fun mappingCacheFile(envType: EnvType): Path =
        (if (stubs.contains(envType)) project.unimined.getLocalCache() else project.unimined.getGlobalCache()).resolve("mappings")
            .resolve("mappings-${getCombinedNames(envType)}-${envType}.jar")

    private fun resolveMappingTree(envType: EnvType): MemoryMappingTree {
        project.logger.lifecycle("Resolving mappings for $envType")
        val hasStub = stubs.contains(envType)
        val file = mappingCacheFile(envType)
        val mappingTree = MemoryMappingTree()
        if (file.exists()) {
            ZipInputStream(file.inputStream()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry ?: throw IllegalStateException("Mappings jar is empty $file")
                while (!entry!!.name.endsWith("mappings.tiny")) {
                    entry = zip.nextEntry
                    if (entry == null) {
                        throw IllegalStateException("No mappings.tiny found in $file")
                    }
                }
                project.logger.info("Found mappings.tiny in $file at ${entry.name}")
                Tiny2Reader2.read(InputStreamReader(zip), mappingTree)
            }
        } else {
            for (mapping in mappingsFiles(envType)) {
                project.logger.info(
                    "mapping ns's already read: ${mappingTree.srcNamespace}, ${
                        mappingTree.dstNamespaces?.joinToString(
                            ", "
                        )
                    }"
                )
                project.logger.info("Reading mappings from $mapping")
                if (mapping.second.extension == "zip" || mapping.second.extension == "jar") {
                    val contents = ZipReader.readContents(mapping.second.toPath())
                    project.logger.info("Detected mapping type: ${ZipReader.getZipTypeFromContentList(contents)}")
                    ZipReader.readMappings(envType, mapping.second.toPath(), contents, mappingTree, when (mapping.first.name) {
                        "yarn" -> MappingNamespace.YARN
                        "quilt-mappings" -> MappingNamespace.QUILT
                        else -> MappingNamespace.YARN
                    })
                } else if (mapping.second.name == "client_mappings.txt" || mapping.second.name == "server_mappings.txt") {
                    project.logger.info("Detected proguard mappings")
                    InputStreamReader(mapping.second.inputStream()).use {
                        ProGuardReader.read(it, MappingNamespace.MOJMAP.namespace, MappingNamespace.OFFICIAL.namespace,
                            MappingSourceNsSwitch(
                                mappingTree,
                                MappingNamespace.OFFICIAL.namespace
                            )
                        )
                    }
                } else {
                    throw IllegalStateException("Unknown mapping file type ${mapping.first} ${mapping.second.name}")
                }
            }
            if (hasStub) {
                getStub(envType).visit(mappingTree)
            }
            if (mappingTree.dstNamespaces.contains("srg")) {
                project.logger.info("Detected TSRG2 mappings (1.17+) - converting to have the right class names for runtime forge")
                // read mojmap (possible again, TODO: detect if already there on named)
                if (!mappingTree.dstNamespaces.contains(MappingNamespace.MOJMAP.namespace)) {
                    val mojmap = getOfficialMappings()
                    mojmap.accept(mappingTree)
                }
                SeargeFromTsrg2.apply("srg", "mojmap", "searge", mappingTree)
            }
            writeToCache(file, mappingTree)
        }
        getInternalMappingsConfig(envType).dependencies.add(
            project.dependencies.create(
                project.files(file.toFile())
            )
        )

        project.logger.info(
            "mappings for $envType, srcNamespace: ${mappingTree.srcNamespace} dstNamespaces: ${
                mappingTree.dstNamespaces.joinToString(
                    ","
                )
            }"
        )
        val available = (mappingTree.dstNamespaces.filter { it != "srg" }.map { MappingNamespace.getNamespace(it) } + MappingNamespace.OFFICIAL).toSet()
        project.logger.lifecycle("found mappings for $envType: $available")
        return mappingTree
    }


    private fun memberOf(className: String, memberName: String, descriptor: String?): IMappingProvider.Member {
        return IMappingProvider.Member(className, memberName, descriptor)
    }

    private fun fixInnerClassName(
        mappings: MappingTreeView,
        fromId: Int,
        toId: Int,
        fromClassName: String,
        toClassName: String?
    ): String? {
        var toClassName = toClassName
        val outerClass = fromClassName.substring(0, fromClassName.lastIndexOf('$'))
        val outerClassDef = mappings.getClass(outerClass, fromId)
        if (outerClassDef != null) {
            val outerFromClassName = outerClassDef.getName(fromId)
            var outerToClassName = outerClassDef.getName(toId)
            if (outerFromClassName != null && outerFromClassName.contains('$')) {
                outerToClassName = fixInnerClassName(
                    mappings,
                    fromId,
                    toId,
                    outerFromClassName,
                    outerToClassName
                )
            }
            val innerClassName = toClassName?.let {
                it.substring(
                    it.lastIndexOf(
                        '$'
                    )
                )
            } ?: fromClassName.substring(fromClassName.lastIndexOf('$'))
            if (outerToClassName != null && (toClassName == null || !toClassName.startsWith(outerToClassName))) {
                toClassName = "$outerToClassName$$innerClassName"
                project.logger.info("Detected missing inner class, replacing with: {} -> {}", fromClassName, toClassName)
            }
        }
        return toClassName
    }

    private open class Mapping(val to: String?)
    private class ClassMapping(val from: String, to: String) : Mapping(to)
    private open class MemberMapping(val from: String, val fromDesc: String?, to: String) : Mapping(to)
    private class MethodMapping(from: String, fromDesc: String, to: String) : MemberMapping(from, fromDesc, to)
    private class FieldMapping(from: String, fromDesc: String?, to: String) : MemberMapping(from, fromDesc, to)
    private class ArgumentMapping(to: String, val index: Int) : Mapping(to)
    private class LocalVariableMapping(to: String, val lvIndex: Int, val startOpIdx: Int, val lvtRowIndex: Int) : Mapping(to)

    private val mappingCache = mutableMapOf<Pair<MappingNamespace, MappingNamespace>, List<Mapping>>()

    private fun getInternalMappingsProvider(
        envType: EnvType,
        remap: Pair<MappingNamespace, MappingNamespace>,
        remapLocalVariables: Boolean,
    ) : List<Mapping> {
        return mappingCache.computeIfAbsent(remap) {
            val reverse = remap.first.shouldReverse(remap.second)
            val srcName = (if (reverse) remap.second else remap.first).namespace
            val dstName = (if (reverse) remap.first else remap.second).namespace

            val mappingTree = getMappingTree(envType)
            val fromId = mappingTree.getNamespaceId(srcName)
            val toId = mappingTree.getNamespaceId(dstName)

            if (fromId == MappingTreeView.NULL_NAMESPACE_ID) {
                throw IllegalArgumentException("Unknown source namespace: $srcName")
            }

            if (toId == MappingTreeView.NULL_NAMESPACE_ID) {
                throw IllegalArgumentException("Unknown target namespace: $dstName")
            }

            val mappings = mutableListOf<Mapping>()

            for (classDef in mappingTree.classes) {
                var fromClassName = classDef.getName(fromId)
                var toClassName = classDef.getName(toId)

                if (fromClassName == null) {
                    project.logger.info("Target class {} has no name in namespace {}", classDef, srcName)
                    fromClassName = toClassName
                }

                // detect missing inner class
                if (fromClassName != null && fromClassName.contains("$")) {
                    toClassName = fixInnerClassName(
                        mappingTree,
                        fromId,
                        toId,
                        fromClassName,
                        toClassName
                    )
                }

                if (toClassName == null) {
                    project.logger.info("Target class {} has no name in namespace {}", classDef, dstName)
                    toClassName = fromClassName
                }

                if (fromClassName == null) {
                    project.logger.info("Class $classDef has no name in either namespace $srcName or $dstName")
                    continue
                }

                if (reverse) {
                    mappings.add(ClassMapping(toClassName, fromClassName))
                } else {
                    mappings.add(ClassMapping(fromClassName, toClassName))
                }

                for (fieldDef in classDef.fields) {
                    val fromFieldName = fieldDef.getName(fromId)
                    val toFieldName = fieldDef.getName(toId)

                    if (fromFieldName == null) {
                        project.logger.info("Target field {} has no name in namespace {}", fieldDef, srcName)
                        continue
                    }

                    if (toFieldName == null) {
                        project.logger.info("Target field {} has no name in namespace {}", fieldDef, dstName)
                        continue
                    }

                    if (reverse) {
                        mappings.add(FieldMapping(toFieldName, fieldDef.getDesc(toId), fromFieldName))
                    } else {
                        mappings.add(FieldMapping(fromFieldName, fieldDef.getDesc(fromId), toFieldName))
                    }
                }

                for (methodDef in classDef.methods) {
                    val fromMethodName = methodDef.getName(fromId)
                    val toMethodName = methodDef.getName(toId)

                    if (fromMethodName == null) {
                        project.logger.info("Target method {} has no name in namespace {}", methodDef, srcName)
                        continue
                    }

                    if (toMethodName == null) {
                        project.logger.info("Target method {} has no name in namespace {}", methodDef, dstName)
                        continue
                    }

                    if (reverse) {
                        mappings.add(MethodMapping(toMethodName, methodDef.getDesc(toId)!!, fromMethodName))
                    } else {
                        mappings.add(MethodMapping(fromMethodName, methodDef.getDesc(fromId)!!, toMethodName))
                    }

                    if (remapLocalVariables) {
                        for (arg in methodDef.args) {
                            val toArgName = if (reverse) arg.getName(fromId) else arg.getName(toId)

                            if (toArgName != null) {
                                mappings.add(ArgumentMapping(toArgName, arg.lvIndex))
                            }
                        }

                        for (localVar in methodDef.vars) {
                            val toLocalVarName = if (reverse) localVar.getName(fromId) else localVar.getName(toId)

                            if (toLocalVarName != null) {
                                mappings.add(
                                    LocalVariableMapping(
                                        toLocalVarName,
                                        localVar.lvIndex,
                                        localVar.startOpIdx,
                                        localVar.lvtRowIndex
                                    )
                                )
                            }
                        }
                    }
                }
            }
            mappings
        }
    }

    @ApiStatus.Internal
    override fun getMappingsProvider(
        envType: EnvType,
        remap: Pair<MappingNamespace, MappingNamespace>,
        remapLocalVariables: Boolean,
    ): (IMappingProvider.MappingAcceptor) -> Unit {
        val mappings = getInternalMappingsProvider(envType, remap, remapLocalVariables)
        return { acceptor ->
            var lastClass: String? = null
            var lastMethod: IMappingProvider.Member? = null
            for (mapping in mappings) {
                when (mapping) {
                    is ClassMapping -> {
                        lastClass = mapping.from
                        lastMethod = null
                        acceptor.acceptClass(mapping.from, mapping.to)
                    }
                    is MethodMapping -> {
                        if (lastClass == null) throw IllegalStateException("Method mapping before class mapping")
                        lastMethod = memberOf(lastClass, mapping.from, mapping.fromDesc)

                        acceptor.acceptMethod(lastMethod, mapping.to)
                    }
                    is FieldMapping -> {
                        if (lastClass == null) throw IllegalStateException("Field mapping before class mapping")
                        lastMethod = null
                        acceptor.acceptField(memberOf(lastClass, mapping.from, mapping.fromDesc), mapping.to)
                    }
                    is ArgumentMapping -> {
                        if (lastMethod == null) throw IllegalStateException("Argument mapping before method mapping")
                        acceptor.acceptMethodArg(lastMethod, mapping.index, mapping.to)
                    }
                    is LocalVariableMapping -> {
                        if (lastMethod == null) throw IllegalStateException("Local variable mapping before method mapping")
                        acceptor.acceptMethodVar(lastMethod, mapping.lvIndex, mapping.startOpIdx, mapping.lvtRowIndex, mapping.to)
                    }
                }
            }
        }
    }

    @ApiStatus.Internal
    override fun getAvailableMappings(envType: EnvType): Set<MappingNamespace> = (getMappingTree(envType).dstNamespaces.map { MappingNamespace.getNamespace(it) } + MappingNamespace.OFFICIAL).toSet()

}