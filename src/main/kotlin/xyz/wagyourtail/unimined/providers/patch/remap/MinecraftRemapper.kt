package xyz.wagyourtail.unimined.providers.patch.remap

import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.*
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.IMappingProvider.MappingAcceptor
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.configurationcache.extensions.capitalized
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

@Suppress("MemberVisibilityCanBePrivate")
class MinecraftRemapper(
    val project: Project,
    val provider: MinecraftProvider,
) {

    var remapFrom = "official"
    var fallbackTarget = "intermediary"
    fun getMappings(envType: EnvType): Configuration {
        return project.configurations.maybeCreate(Constants.MAPPINGS_PROVIDER + (envType.classifier?.capitalized() ?: ""))
    }

    private fun getInternalMappingsConfig(envType: EnvType): Configuration {
        return project.configurations.maybeCreate(Constants.MAPPINGS_INTERNAL + (envType.classifier?.capitalized() ?: ""))
    }

    private val mappingTrees = mutableMapOf<EnvType, MappingTreeView>()
    fun getMappingTree(envType: EnvType): MappingTreeView {
        return mappingTrees.computeIfAbsent(envType, ::resolveMappingTree)
    }

    private fun resolveMappingTree(envType: EnvType): MemoryMappingTree {
        val file = provider.minecraftDownloader.mcVersionFolder(provider.minecraftDownloader.version).resolve("mappings-${getCombinedNames(envType)}-${envType}.jar")
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
                if (mapping.extension == "zip" || mapping.extension == "jar") {
                    val contents = ZipReader.readContents(mapping.toPath())
                    project.logger.info("Detected mapping type: ${ZipReader.getZipTypeFromContentList(contents)}")
                    ZipReader.readMappings(envType, mapping.toPath(), contents, mappingTree)
                } else if (mapping.name == "client_mappings.txt" || mapping.name == "server_mappings.txt") {
                    InputStreamReader(mapping.inputStream()).use {
                        val temp = MemoryMappingTree()
                        MappingReader.read(it, temp)
                        temp.srcNamespace = "named"
                        temp.dstNamespaces = listOf(mappingTree.srcNamespace)
                        temp.accept(MappingSourceNsSwitch(mappingTree, mappingTree.srcNamespace))
                    }
                } else {
                    throw IllegalStateException("Unknown mapping file type ${mapping.name}")
                }
            }
            writeToFile(file, mappingTree)
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
        return mappingTree
    }


    private val mappingFileEnvs = mutableMapOf<EnvType, Set<File>>()

    fun mappingsFiles(envType: EnvType): Set<File> {
        return mappingFileEnvs.computeIfAbsent(envType) {
            if (envType != EnvType.COMBINED) {
                mappingsFiles(EnvType.COMBINED) + getMappings(envType).files
            } else {
                getMappings(envType).files
            }
        }
    }

    private fun memberOf(className: String, memberName: String, descriptor: String?): IMappingProvider.Member {
        return IMappingProvider.Member(className, memberName, descriptor)
    }

    fun getMappingProvider(
        srcName: String,
        fallbackTarget: String,
        targetName: String,
        mappingTree: MappingTreeView,
        remapLocalVariables: Boolean = true,
    ): (MappingAcceptor) -> Unit {
        return { acceptor: MappingAcceptor ->
            val fromId = mappingTree.getNamespaceId(srcName)
            var fallbackId = mappingTree.getNamespaceId(fallbackTarget)
            val toId = mappingTree.getNamespaceId(targetName)

            if (toId == MappingTreeView.NULL_NAMESPACE_ID) {
                throw RuntimeException("Namespace $targetName not found in mappings")
            }

            if (fallbackId == MappingTreeView.NULL_NAMESPACE_ID) {
                fallbackId = fromId
            }

            for (classDef in mappingTree.classes) {
                val fromClassName = classDef.getName(fromId) ?: classDef.getName(fallbackId)
                val toClassName = classDef.getName(toId) ?: classDef.getName(fallbackId) ?: fromClassName

                if (fromClassName == null) {
                    project.logger.warn("No class name for $classDef")
                }
                acceptor.acceptClass(fromClassName, toClassName)

                for (fieldDef in classDef.fields) {
                    val fromFieldName = fieldDef.getName(fromId) ?: fieldDef.getName(fallbackId)
                    val toFieldName = fieldDef.getName(toId) ?: fieldDef.getName(fallbackId) ?: fromFieldName
                    if (fromFieldName == null) {
                        project.logger.warn("No field name for $toFieldName")
                        continue
                    }
                    acceptor.acceptField(memberOf(fromClassName, fromFieldName, fieldDef.getDesc(fromId)), toFieldName)
                }

                for (methodDef in classDef.methods) {
                    val fromMethodName = methodDef.getName(fromId) ?: methodDef.getName(fallbackId)
                    val toMethodName = methodDef.getName(toId) ?: methodDef.getName(fallbackId) ?: fromMethodName
                    val fromMethodDesc = methodDef.getDesc(fromId) ?: methodDef.getDesc(fallbackId)
                    if (fromMethodName == null) {
                        project.logger.warn("No method name for $toMethodName")
                        continue
                    }
                    val method = memberOf(fromClassName, fromMethodName, fromMethodDesc)

                    acceptor.acceptMethod(method, toMethodName)

                    if (remapLocalVariables) {
                        for (arg in methodDef.args) {
                            val toArgName = arg.getName(toId) ?: arg.getName(fallbackId) ?: continue
                            acceptor.acceptMethodArg(method, arg.lvIndex, toArgName)
                        }

                        for (localVar in methodDef.vars) {
                            val toLocalVarName = localVar.getName(toId) ?: localVar.getName(fallbackId) ?: continue
                            acceptor.acceptMethodVar(
                                method,
                                localVar.lvIndex,
                                localVar.startOpIdx,
                                localVar.lvtRowIndex,
                                toLocalVarName
                            )
                        }
                    }
                }
            }
        }
    }

    fun writeToFile(file: Path, mappingTree: MappingTreeView) {
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("mappings/mappings.tiny"))
            OutputStreamWriter(it).let { writer ->
                mappingTree.accept(Tiny2Writer2(writer, false))
                writer.flush()
            }
            it.closeEntry()
        }
    }

    private val combinedNamesMap = mutableMapOf<EnvType, String>()

    fun getCombinedNames(envType: EnvType): String {
        return combinedNamesMap.computeIfAbsent(envType) {
            val thisEnv = getMappings(envType).dependencies.toMutableSet()
            if (envType != EnvType.COMBINED) {
                thisEnv.addAll(getMappings(EnvType.COMBINED).dependencies ?: setOf())
            }
            val jarMod = thisEnv.sortedBy { "${it.name}-${it.version}" }
            jarMod.joinToString("+") { it.name + "-" + it.version }
        }
    }

    fun provide(envType: EnvType, file: Path, remapTo: String, remapFrom: String = this.remapFrom): Path {
        val parent = file.parent
        val target = parent.resolve(getCombinedNames(envType))
            .resolve("${file.nameWithoutExtension}-mapped-${getCombinedNames(envType)}-${remapTo}.${file.extension}")

        if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            return target
        }

        val remapper = TinyRemapper.newRemapper().withMappings(getMappingProvider(remapFrom, fallbackTarget, remapTo, getMappingTree(envType)))
            .renameInvalidLocals(true)
            .inferNameFromSameLvIndex(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .checkPackageAccess(true)
            .fixPackageAccess(true)
            .rebuildSourceFilenames(true)
            .ignoreConflicts(true)
            .ignoreFieldDesc(true)
            .build()
        project.logger.warn("Remapping ${file.name} to $target")

        OutputConsumerPath.Builder(target).build().use {
            it.addNonClassFiles(file, NonClassCopyMode.FIX_META_INF, null)
            remapper.readInputs(file)
            remapper.apply(it)
        }
        remapper.finish()
        return target
    }
}