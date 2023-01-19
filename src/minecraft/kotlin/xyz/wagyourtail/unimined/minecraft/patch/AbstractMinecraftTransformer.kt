package xyz.wagyourtail.unimined.minecraft.patch

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.MinecraftPatcher
import xyz.wagyourtail.unimined.api.run.RunConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.transform.fixes.FixParamAnnotations
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

abstract class AbstractMinecraftTransformer protected constructor(
    protected val project: Project,
    val provider: MinecraftProviderImpl
) : MinecraftPatcher {
    @ApiStatus.Internal
    open fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar): MinecraftJar {
        //TODO: do this for real
        return clientjar
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
}