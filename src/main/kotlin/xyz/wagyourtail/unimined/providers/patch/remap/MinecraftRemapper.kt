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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import java.io.File
import java.io.InputStream
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

    //TODO: make this configurable
    var remapFrom = "official"
    var fallbackTarget = "intermediary"

    val mappings: Configuration = project.configurations.maybeCreate(Constants.MAPPINGS_PROVIDER)
    private val internalMappingsConfig: Configuration = project.configurations.maybeCreate(Constants.MAPPINGS_INTERNAL)
    private val internalMappingsConfigServer: Configuration = project.configurations.maybeCreate(Constants.MAPPINGS_INTERNAL_SERVER)

    val mappingTree: MemoryMappingTree by lazy {
        _mappingTree(MinecraftProvider.EnvType.CLIENT)
    }

    val mappingTreeServer: MemoryMappingTree by lazy {
        _mappingTree(MinecraftProvider.EnvType.SERVER)
    }

    private fun _mappingTree(envType: MinecraftProvider.EnvType): MemoryMappingTree {
        val file = provider.minecraftDownloader.mcVersionFolder(provider.minecraftDownloader.version).resolve("mappings-${combinedNames}-${envType}.jar")
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
            for (mapping in mappingsFiles) {
                if (mapping.extension == "zip" || mapping.extension == "jar") {
                    forEachInZip(mapping) { type, stream ->
                        when (type) {
                            MappingType.TINY_2 -> MappingReader.read(InputStreamReader(stream), mappingTree)
                            MappingType.MCP_METHODS -> MCPReader.readMethod(InputStreamReader(stream), "searge", "named", mappingTree)
                            MappingType.MCP_PARAMS -> MCPReader.readParam(InputStreamReader(stream), "searge", "named", mappingTree)
                            MappingType.MCP_FIELDS -> MCPReader.readField(InputStreamReader(stream), "searge", "named", mappingTree)
                            MappingType.SRG_CLIENT -> {
                                if (envType != MinecraftProvider.EnvType.SERVER)
                                    SrgReader.read(InputStreamReader(stream), "official", "searge", MappingSourceNsSwitch(mappingTree, "searge"))
                            }
                            MappingType.SRG_SERVER -> {
                                if (envType != MinecraftProvider.EnvType.CLIENT)
                                    SrgReader.read(InputStreamReader(stream), "official", "searge",  MappingSourceNsSwitch(mappingTree, "searge"))
                            }
                            MappingType.SRG_MERGED -> {
                                SrgReader.read(InputStreamReader(stream), "official", "searge",  MappingSourceNsSwitch(mappingTree, "searge"))
                            }
                        }
                    }
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
        if (envType == MinecraftProvider.EnvType.SERVER) {
            internalMappingsConfigServer.dependencies.add(
                project.dependencies.create(
                    project.files(file.toFile())
                )
            )
        } else {
            internalMappingsConfig.dependencies.add(
                project.dependencies.create(
                    project.files(file.toFile())
                )
            )
        }

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        sourceSets.findByName("main")?.let {
            it.runtimeClasspath += if (envType == MinecraftProvider.EnvType.SERVER) internalMappingsConfigServer else internalMappingsConfig
        }
        if (envType == MinecraftProvider.EnvType.CLIENT) {
            sourceSets.findByName("client")?.let {
                it.runtimeClasspath += internalMappingsConfig
            }
        }
        if (envType == MinecraftProvider.EnvType.SERVER) {
            sourceSets.findByName("server")?.let {
                it.runtimeClasspath += internalMappingsConfigServer
            }
        }
        return mappingTree
    }

    val mappingsFiles: Set<File> by lazy {
        val dependencies = mappings.dependencies

        if (dependencies.isEmpty()) {
            throw IllegalStateException("No dependencies found for mappings provider")
        }

        mappings.files
    }

    private fun forEachInZip(zip: File, action: (MappingType, InputStream) -> Unit) {
        val zipEntryList = mutableListOf<String>()
        ZipInputStream(zip.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry ?: throw IllegalStateException("Mappings jar is empty $zip")
            while (entry != null) {
                zipEntryList.add(entry.name)
                entry = zip.nextEntry
            }
        }

        val filterSrgSides = zipEntryList.any { it.matches(MappingType.SRG_MERGED.pattern) }

        ZipInputStream(zip.inputStream()).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                if (entry!!.isDirectory) {
                    entry = stream.nextEntry
                    continue
                }
                for (mappingT in MappingType.values()) {
                    if (entry!!.name.matches(mappingT.pattern)) {
                        if (filterSrgSides && mappingT == MappingType.SRG_CLIENT || mappingT == MappingType.SRG_SERVER) {
                            continue
                        }
                        project.logger.warn("Found mapping file ${entry!!.name}")
                        action(mappingT, stream)
                        break
                    }
                }
                entry = stream.nextEntry
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

            for (classDef in (mappingTree as MappingTreeView).classes) {
                val fromClassName = classDef.getName(fromId) ?: classDef.getName(fallbackId)
                val toClassName = classDef.getName(toId) ?: classDef.getName(fallbackId) ?: fromClassName

                if (fromClassName == null) {
                    project.logger.warn("No class name for $fromClassName")
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

    val combinedNames: String by lazy {
        val mappingsDependecies = (mappings.dependencies as Set<Dependency>).sortedBy { "${it.name}-${it.version}" }
        mappingsDependecies.joinToString("+") { it.name + "-" + it.version }
    }

    fun provideClient(file: Path, remapTo: String, remapFrom: String = this.remapFrom): Path {
        mappingTree
        val parent = file.parent
        val target = parent.resolve(combinedNames)
            .resolve("${file.nameWithoutExtension}-mapped-${combinedNames}-${remapTo}.${file.extension}")

        if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            return target
        }

        val remapper = TinyRemapper.newRemapper().withMappings(getMappingProvider(remapFrom, fallbackTarget, remapTo, mappingTree))
            .renameInvalidLocals(true)
            .inferNameFromSameLvIndex(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .checkPackageAccess(true)
            .fixPackageAccess(true)
            .rebuildSourceFilenames(true)
            .ignoreConflicts(true)
            .ignoreFieldDesc(true)
            .build()


        OutputConsumerPath.Builder(target).build().use {
            it.addNonClassFiles(file, NonClassCopyMode.FIX_META_INF, null)
            remapper.readInputs(file)
            remapper.apply(it)
        }
        remapper.finish()
        return target
    }

    fun provideServer(file: Path, remapTo: String, remapFrom: String = this.remapFrom): Path {
        mappingTreeServer
        val parent = file.parent
        val target = parent.resolve(combinedNames)
            .resolve("${file.nameWithoutExtension}-mapped-${combinedNames}-${remapTo}.${file.extension}")

        if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            return target
        }

        val remapper = TinyRemapper.newRemapper().withMappings(getMappingProvider(remapFrom, fallbackTarget, remapTo, mappingTreeServer))
            .renameInvalidLocals(true)
            .inferNameFromSameLvIndex(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .checkPackageAccess(true)
            .fixPackageAccess(true)
            .rebuildSourceFilenames(true)
            .ignoreConflicts(true)
            .ignoreFieldDesc(true)
            .build()


        OutputConsumerPath.Builder(target).build().use {
            it.addNonClassFiles(file, NonClassCopyMode.FIX_META_INF, null)
            remapper.readInputs(file)
            remapper.apply(it)
        }
        remapper.finish()
        return target
    }

    enum class MappingType(val pattern: Regex) {
        MCP_METHODS(Regex(".+/methods.csv$")),
        MCP_PARAMS(Regex(".+/params.csv$")),
        MCP_FIELDS(Regex(".+/fields.csv$")),
        SRG_CLIENT(Regex(".+/client.srg$")),
        SRG_SERVER(Regex(".+/server.srg$")),
        SRG_MERGED(Regex(".+/merged.srg$")),
        TINY_2(Regex(".+/mappings.tiny$"));
    }
}