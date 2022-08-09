package xyz.wagyourtail.unimined.providers.patch.remap

import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingWriter
import net.fabricmc.mappingio.format.MappingFormat
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping
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
    val remapFrom = "obf"
    val fallbackTarget = "intermediary"

    val mappings: Configuration = project.configurations.maybeCreate(Constants.MAPPINGS_PROVIDER)
    private val internalMappingsConfig: Configuration = project.configurations.maybeCreate(Constants.MAPPINGS_INTERNAL)

    val mappingTree: MemoryMappingTree by lazy {
        val file = provider.minecraftDownloader.mcVersionFolder(provider.minecraftDownloader.version).resolve("mappings-${combinedNames}.jar")
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
                MappingReader.read(InputStreamReader(zip), mappingTree)
            }
        } else {
            for (mapping in mappingsFiles) {
                forEachInZip(mapping) { stream ->
                    MappingReader.read(InputStreamReader(stream), mappingTree)
                }
            }
            writeToFile(file, mappingTree)
        }
        internalMappingsConfig.dependencies.add(
            project.dependencies.create(
                project.files(file.toFile())
            )
        )

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        sourceSets.findByName("main")?.let {
            it.runtimeClasspath += internalMappingsConfig
        }
        sourceSets.findByName("client")?.let {
            it.runtimeClasspath += internalMappingsConfig
        }
        sourceSets.findByName("server")?.let {
            it.runtimeClasspath += internalMappingsConfig
        }

        mappingTree
    }

    val mappingsFiles: Set<File> by lazy {
        val dependencies = mappings.dependencies

        if (dependencies.isEmpty()) {
            throw IllegalStateException("No dependencies found for mappings provider")
        }

        mappings.resolve()
    }

    private fun forEachInZip(zip: File, action: (InputStream) -> Unit) {
        ZipInputStream(zip.inputStream()).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                if (entry.isDirectory) {
                    entry = stream.nextEntry
                    continue
                }
                if (entry.name.contains("META-INF") || entry.name.contains("extras")) {
                    entry = stream.nextEntry
                    continue
                }
                project.logger.info("Reading ${entry.name}")
                action(stream)
                entry = stream.nextEntry
            }
        }
    }

    private fun memberOf(className: String, memberName: String, descriptor: String): IMappingProvider.Member {
        return IMappingProvider.Member(className, memberName, descriptor)
    }

    fun getMappingProvider(
        srcName: String,
        fallbackTarget: String,
        targetName: String,
        remapLocalVariables: Boolean = true
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

            @Suppress("INACCESSIBLE_TYPE")
            for (classDef in mappingTree.classes as Collection<ClassMapping>) {
                val fromClassName = classDef.getName(fromId)
                val toClassName = classDef.getName(toId) ?: classDef.getName(fallbackId) ?: fromClassName

                acceptor.acceptClass(fromClassName, toClassName)

                for (fieldDef in classDef.fields) {
                    val fromFieldName = fieldDef.getName(fromId)
                    val toFieldName = fieldDef.getName(toId) ?: fieldDef.getName(fallbackId) ?: fromFieldName

                    acceptor.acceptField(memberOf(fromClassName, fromFieldName, fieldDef.getDesc(fromId)), toFieldName)
                }

                for (methodDef in classDef.methods) {
                    val fromMethodName = methodDef.getName(fromId)
                    val toMethodName = methodDef.getName(toId) ?: methodDef.getName(fallbackId) ?: fromMethodName
                    val fromMethodDesc = methodDef.getDesc(fromId)

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
            mappingTree.accept(MappingWriter.create(OutputStreamWriter(it), MappingFormat.TINY_2))
            it.closeEntry()
        }
    }

    val combinedNames: String by lazy {
        val mappingsDependecies = (mappings.dependencies as Set<Dependency>).sortedBy { "${it.name}-${it.version}" }
        mappingsDependecies.joinToString("+") { it.name + "-" + it.version }
    }

    fun provide(file: Path, remapTo: String, remapFrom: String = this.remapFrom): Path {
        mappingTree
        val parent = file.parent
        val target = parent.resolve(combinedNames)
            .resolve("${file.nameWithoutExtension}-mapped-${combinedNames}-${remapTo}.${file.extension}")

        if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            return target
        }

        val remapper = TinyRemapper.newRemapper().withMappings(getMappingProvider(remapFrom, fallbackTarget, remapTo))
            .renameInvalidLocals(true)
            .inferNameFromSameLvIndex(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .checkPackageAccess(true)
            .fixPackageAccess(true)
            .rebuildSourceFilenames(true)
            .build()


        OutputConsumerPath.Builder(target).build().use {
            it.addNonClassFiles(file, NonClassCopyMode.FIX_META_INF, null)
            remapper.readInputs(file)
            remapper.apply(it)
        }
        remapper.finish()
        return target
    }
}