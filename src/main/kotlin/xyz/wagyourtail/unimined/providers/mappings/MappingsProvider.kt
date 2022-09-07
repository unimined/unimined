package xyz.wagyourtail.unimined.providers.mappings

import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.*
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.UniminedExtension
import xyz.wagyourtail.unimined.maybeCreate
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.writer

abstract class MappingsProvider(
    val project: Project, val parent: UniminedExtension
) {

    init {
        for (envType in EnvType.values()) {
            getMappings(envType)
        }
    }

    private val mappingExports = mutableListOf<MappingExport>()

    fun addExport(envType: String, export: (MappingExport) -> Unit) {
        addExport(EnvType.valueOf(envType), false, export)
    }

    @ApiStatus.Internal
    fun addExport(envType: EnvType, now: Boolean = false, export: (MappingExport) -> Unit) {
        val me = MappingExport(envType)
        export(me)
        if (me.location != null && me.type != null) {
            mappingExports.add(me)
            if (mappingTrees.contains(envType)) {
                project.logger.warn("Mappings for $envType already exist, exporting ${me.location} directly.")
                me.export(mappingTrees[envType]!!)
            } else {
                if (now) {
                    getMappingTree(envType)
                }
            }
        }
    }

    @ApiStatus.Internal
    fun getMappings(envType: EnvType): Configuration {
        return project.configurations.maybeCreate(
            Constants.MAPPINGS_PROVIDER + (envType.classifier?.capitalized() ?: "")
        )
    }

    @ApiStatus.Internal
    fun hasStubs(envType: EnvType): Boolean {
        return stubs.contains(envType)
    }

    fun getStub(envType: String) = getStub(EnvType.valueOf(envType))

    private val stubs = mutableMapOf<EnvType, MemoryMapping>()

    private fun getStub(envType: EnvType): MemoryMapping {
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
    fun getMappingTree(envType: EnvType): MappingTreeView {
        return mappingTrees.computeIfAbsent(envType, ::resolveMappingTree)
    }

    private val combinedNamesMap = mutableMapOf<EnvType, String>()

    @ApiStatus.Internal
    fun getCombinedNames(envType: EnvType): String {
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

    private val mappingFileEnvs = mutableMapOf<EnvType, Set<File>>()

    @Synchronized
    private fun mappingsFiles(envType: EnvType): Set<File> {
        return if (envType != EnvType.COMBINED) {
            mappingsFiles(EnvType.COMBINED)
        } else {
            setOf()
        } + mappingFileEnvs.computeIfAbsent(envType) {
            getMappings(envType).resolve()
        }
    }

    private fun writeToCache(file: Path, mappingTree: MappingTreeView) {
        file.parent.maybeCreate()
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
    fun mappingCacheFile(envType: EnvType) =
        (if (stubs.contains(envType)) parent.getLocalCache() else parent.getGlobalCache()).resolve("mappings")
            .resolve("mappings-${getCombinedNames(envType)}-${envType}.jar")

    private fun resolveMappingTree(envType: EnvType): MemoryMappingTree {
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
                project.logger.warn("Found mappings.tiny in $file at ${entry.name}")
                Tiny2Reader2.read(InputStreamReader(zip), mappingTree)
            }
        } else {
            for (mapping in mappingsFiles(envType)) {
                project.logger.warn("mapping ns's already read: ${mappingTree.srcNamespace}, ${mappingTree.dstNamespaces?.joinToString(", ")}")
                project.logger.warn("Reading mappings from $mapping")
                if (mapping.extension == "zip" || mapping.extension == "jar") {
                    val contents = ZipReader.readContents(mapping.toPath())
                    project.logger.warn("Detected mapping type: ${ZipReader.getZipTypeFromContentList(contents)}")
                    ZipReader.readMappings(envType, mapping.toPath(), contents, mappingTree)
                } else if (mapping.name == "client_mappings.txt" || mapping.name == "server_mappings.txt") {
                    project.logger.warn("Detected proguard mappings")
                    InputStreamReader(mapping.inputStream()).use {
                        ProGuardReader.read(it, "named", "official", MappingSourceNsSwitch(mappingTree, mappingTree.srcNamespace))
                    }
                } else {
                    throw IllegalStateException("Unknown mapping file type ${mapping.name}")
                }
            }
            if (hasStub) {
                getStub(envType).visit(mappingTree)
            }
            writeToCache(file, mappingTree)
        }
        getInternalMappingsConfig(envType).dependencies.add(
            project.dependencies.create(
                project.files(file.toFile())
            )
        )

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        if (envType == EnvType.COMBINED) {
            sourceSets.findByName("main")?.let {
                it.runtimeClasspath += getInternalMappingsConfig(envType)
            }
        }
        if (envType == EnvType.CLIENT) {
            sourceSets.findByName("client")?.let {
                it.runtimeClasspath += getInternalMappingsConfig(envType)
            }
        }
        if (envType == EnvType.SERVER) {
            sourceSets.findByName("server")?.let {
                it.runtimeClasspath += getInternalMappingsConfig(envType)
            }
        }
        project.logger.warn(
            "mappings for $envType, srcNamespace: ${mappingTree.srcNamespace} dstNamespaces: ${
                mappingTree.dstNamespaces.joinToString(
                    ","
                )
            }"
        )
        for (export in mappingExports) {
            if (export.envType == envType) {
                project.logger.warn("Exporting ${export.location}")
                export.export(mappingTree)
            } else {
                project.logger.warn("Skipping export ${export.location} for $envType")
            }
        }
        return mappingTree
    }


    private fun memberOf(className: String, memberName: String, descriptor: String?): IMappingProvider.Member {
        return IMappingProvider.Member(className, memberName, descriptor)
    }

    @ApiStatus.Internal
    fun getMappingProvider(
        envType: EnvType,
        srcName: String,
        fallbackSrc: String,
        fallbackTarget: String,
        targetName: String,
        remapLocalVariables: Boolean = true,
    ): (IMappingProvider.MappingAcceptor) -> Unit {
        val mappingTree = getMappingTree(envType)
        return { acceptor: IMappingProvider.MappingAcceptor ->
            val fromId = mappingTree.getNamespaceId(srcName)
            var fallbackSrcId = mappingTree.getNamespaceId(fallbackSrc)
            var fallbackToId = mappingTree.getNamespaceId(fallbackTarget)
            val toId = mappingTree.getNamespaceId(targetName)

            if (toId == MappingTreeView.NULL_NAMESPACE_ID) {
                throw RuntimeException("Namespace $targetName not found in mappings")
            }

            if (fallbackToId == MappingTreeView.NULL_NAMESPACE_ID) {
                project.logger.warn("Namespace $fallbackTarget not found in mappings, using $srcName as fallback")
                fallbackToId = fromId
            }

            if (fallbackSrcId == MappingTreeView.NULL_NAMESPACE_ID) {
                project.logger.warn("Namespace $fallbackSrc not found in mappings, using $srcName as fallback")
                fallbackSrcId = fromId
            }

            project.logger.debug("Mapping from $srcName to $targetName, fallbackSrc: $fallbackSrc, fallbackTarget: $fallbackTarget")
            project.logger.debug("ids: from $fromId to $toId fallbackTo $fallbackToId fallbackFrom $fallbackSrcId")

            for (classDef in mappingTree.classes) {
                var fromClassName = classDef.getName(fromId) ?: classDef.getName(fallbackSrcId)
                var toClassName = classDef.getName(toId) ?: classDef.getName(fallbackToId)

                if (fromClassName == null) {
                    project.logger.warn("From class name not found for ${classDef}")
                    fromClassName = toClassName
                }
                if (toClassName == null) {
                    project.logger.warn("To class name not found for ${classDef}")
                    toClassName = fromClassName
                }

                if (fromClassName == null) {
                    project.logger.error("Class name not found for ${classDef}")
                }

                project.logger.debug("fromClassName: $fromClassName toClassName: $toClassName")
                if (toClassName != null) {
                    acceptor.acceptClass(fromClassName, toClassName)
                }

                for (fieldDef in classDef.fields) {
                    val fromFieldName = fieldDef.getName(fromId) ?: fieldDef.getName(fallbackSrcId)
                    val toFieldName = fieldDef.getName(toId) ?: fieldDef.getName(fallbackToId)
                    if (fromFieldName == null || toFieldName == null) {
                        project.logger.warn("No field name for $fieldDef")
                        continue
                    }
                    project.logger.debug("fromFieldName: $fromFieldName toFieldName: $toFieldName")
                    acceptor.acceptField(
                        memberOf(fromClassName, fromFieldName, fieldDef.getDesc(fromId)), toFieldName
                    )
                }

                for (methodDef in classDef.methods) {
                    val fromMethodName = methodDef.getName(fromId) ?: methodDef.getName(fallbackSrcId)
                    val toMethodName = methodDef.getName(toId) ?: methodDef.getName(fallbackToId)
                    val fromMethodDesc = methodDef.getDesc(fromId) ?: methodDef.getDesc(fallbackSrcId)
                    if (fromMethodName == null || toMethodName == null) {
                        project.logger.warn("No method name for $methodDef")
                        continue
                    }
                    val method = memberOf(fromClassName, fromMethodName, fromMethodDesc)

                    project.logger.debug("fromMethodName: $fromMethodName toMethodName: $toMethodName")
                    acceptor.acceptMethod(method, toMethodName)

                    if (remapLocalVariables) {
                        for (arg in methodDef.args) {
                            val toArgName = arg.getName(toId) ?: arg.getName(fallbackToId) ?: continue
                            acceptor.acceptMethodArg(method, arg.lvIndex, toArgName)
                        }

                        for (localVar in methodDef.vars) {
                            val toLocalVarName = localVar.getName(toId) ?: localVar.getName(fallbackToId) ?: continue
                            acceptor.acceptMethodVar(
                                method, localVar.lvIndex, localVar.startOpIdx, localVar.lvtRowIndex, toLocalVarName
                            )
                        }
                    }
                }
            }
        }
    }

    inner class MappingExport(val envType: EnvType) {
        @set:ApiStatus.Internal
        var type: MappingExportTypes? = null
        var location: File? = null
        var sourceNamespace: String? = null
        var targetNamespace: List<String>? = null

        fun setType(type: String) {
            this.type = MappingExportTypes.valueOf(type.uppercase(Locale.getDefault()))
        }

        @ApiStatus.Internal
        fun export(mappingTree: MappingTreeView) {
            project.logger.warn("Exporting mappings for $envType to $location")
            location!!.toPath()
                .writer(StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .use { writer ->
                    mappingTree.accept(
                        MappingSourceNsSwitch(
                            MappingDstNsFilter(
                                if (type == MappingExportTypes.TINY_V2) {
                                    Tiny2Writer2(writer, false)
                                } else {
                                    SrgWriter(writer)
                                }, targetNamespace ?: mappingTree.dstNamespaces
                            ), sourceNamespace ?: mappingTree.srcNamespace
                        )
                    )
                    writer.flush()
                }
        }
    }
}

enum class MappingExportTypes {
    TINY_V2, SRG
}