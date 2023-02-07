package xyz.wagyourtail.unimined.minecraft.patch

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.annotations.ApiStatus
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.MinecraftPatcher
import xyz.wagyourtail.unimined.api.run.RunConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.transform.fixes.FixParamAnnotations
import xyz.wagyourtail.unimined.minecraft.transform.merge.ClassMerger
import xyz.wagyourtail.unimined.util.GlobToRegex
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

abstract class AbstractMinecraftTransformer protected constructor(
    protected val project: Project,
    val provider: MinecraftProviderImpl,
    val providerName: String
) : MinecraftPatcher {

    open val merger: ClassMerger = ClassMerger()

    fun isAnonClass(node: ClassNode): Boolean =
        node.innerClasses?.firstOrNull { it.name == node.name }.let { it != null && it.innerName == null}

    open fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar): MinecraftJar {
        val merged = MinecraftJar(
            clientjar,
            envType = EnvType.COMBINED,
            patches = listOf("$providerName-merged") + clientjar.patches + serverjar.patches
        )

        if (merged.path.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            return merged
        }

        merged.path.deleteIfExists()
        ZipReader.openZipFileSystem(merged.path, mapOf("create" to true, "mutable" to true)).use { merged ->
            val clientClassEntries = mutableMapOf<String, ClassNode>()
            ZipReader.forEachInZip(clientjar.path) { path, stream ->
                if (path.startsWith("META-INF/")) return@forEachInZip
                if (path.endsWith(".class")) {
                    if (shouldStripClass(path)) return@forEachInZip
                    // add entry
                    val classReader = ClassReader(stream)
                    val classNode = ClassNode()
                    classReader.accept(classNode, 0)
                    clientClassEntries[path] = classNode
                } else {
                    // copy directly
                    val mergedPath = merged.getPath(path)
                    mergedPath.parent?.createDirectories()
                    mergedPath.writeBytes(stream.readBytes())
                }
            }
            val serverClassEntries = mutableMapOf<String, ClassNode>()
            ZipReader.forEachInZip(serverjar.path) { path, stream ->
                if (path.startsWith("META-INF/")) return@forEachInZip
                if (path.endsWith(".class")) {
                    if (shouldStripClass(path)) return@forEachInZip
                    // add entry
                    val classReader = ClassReader(stream)
                    val classNode = ClassNode()
                    classReader.accept(classNode, 0)
                    serverClassEntries[path] = classNode
                } else {
                    // copy directly
                    val mergedPath = merged.getPath(path)
                    mergedPath.parent?.createDirectories()
                    mergedPath.writeBytes(stream.readBytes())
                }
            }
            // merge classes
            for ((name, node) in clientClassEntries) {
                val classWriter = ClassWriter(0)
                val serverNode = serverClassEntries[name]
                val out = try {
                    merger.accept(node, serverNode)
                } catch (e: Exception) {
                    throw RuntimeException("Error merging class $name", e)
                }
                out.accept(classWriter)
                val path = merged.getPath(name)
                path.parent?.createDirectories()
                path.writeBytes(classWriter.toByteArray())
                serverClassEntries.remove(name)
            }
            for ((name, node) in serverClassEntries) {
                val classWriter = ClassWriter(0)
                val out = merger.accept(null, node)
                out.accept(classWriter)
                val path = merged.getPath(name)
                path.parent?.createDirectories()
                path.writeBytes(classWriter.toByteArray())
            }
        }
        return merged
    }

    protected open val transform = listOf<(FileSystem) -> Unit>(
        FixParamAnnotations::apply
    )

    @ApiStatus.Internal
    open fun transform(minecraft: MinecraftJar): MinecraftJar {
        val target = MinecraftJar(
            minecraft,
            patches = minecraft.patches + listOf("fixed")
        )

        if (target.path.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            return target
        }

        try {
            Files.copy(minecraft.path, target.path, StandardCopyOption.REPLACE_EXISTING)
            ZipReader.openZipFileSystem(target.path, mapOf("mutable" to true)).use { out ->
                transform.forEach { it(out) }
            }
        } catch (e: Exception) {
            target.path.deleteIfExists()
            throw e
        }
        return target
    }
    private fun applyRunConfigs(tasks: TaskContainer) {
        if (provider.runs.off) return
        project.logger.lifecycle("Applying run configs")
        project.logger.info("client: ${provider.client}, server: ${provider.server}")
        if (provider.minecraft.client) {
            project.logger.info("client config")
            applyClientRunConfig(tasks, provider.runs.client)
        }
        if (provider.minecraft.server) {
            project.logger.info("server config")
            applyServerRunConfig(tasks, provider.runs.server)
        }
    }

    @ApiStatus.Internal
    open fun applyClientRunConfig(tasks: TaskContainer, action: (RunConfig) -> Unit = {}) {
        provider.provideVanillaRunClientTask(tasks, action)
    }

    @ApiStatus.Internal
    open fun applyServerRunConfig(tasks: TaskContainer, action: (RunConfig) -> Unit = {}) {
        provider.provideVanillaRunServerTask(tasks, action)
    }

    @ApiStatus.Internal
    open fun afterEvaluate() {
        project.unimined.events.register(::sourceSets)
        project.unimined.events.register(::applyRunConfigs)
    }

    @ApiStatus.Internal
    open fun sourceSets(sourceSets: SourceSetContainer) {
    }

    @ApiStatus.Internal
    open fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar {
        return baseMinecraft
    }

    @ApiStatus.Internal
    open fun afterRemapJarTask(output: Path) {
        // do nothing
    }

    private val includeGlobs = listOf(
        "*",
        "META-INF/**",
        "net/minecraft/**",
        "com/mojang/blaze3d/**"
    ).map { Regex(GlobToRegex.apply(it)) }

    /*
     * only accurate on official mappings
     */
    open fun shouldStripClass(path: String): Boolean {
        // check if in include globs
        for (glob in includeGlobs) {
            if (glob.matches(path)) return false
        }
        // otherwise strip
        return true
    }
}