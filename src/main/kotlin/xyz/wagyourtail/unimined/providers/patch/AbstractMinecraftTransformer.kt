package xyz.wagyourtail.unimined.providers.patch

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.providers.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.remap.RemapJarTask
import java.nio.file.Path

abstract class AbstractMinecraftTransformer protected constructor(
    protected val project: Project,
    val provider: MinecraftProvider
) {
    @ApiStatus.Internal
    open fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar, output: Path): MinecraftJar {
        //TODO: do this for real
        return clientjar
    }

    @ApiStatus.Internal
    abstract fun transform(minecraft: MinecraftJar): MinecraftJar

    private fun applyRunConfigs(tasks: TaskContainer) {
        project.logger.warn("client: ${provider.client}, server: ${provider.server}")
        if (provider.minecraftDownloader.client) {
            applyClientRunConfig(tasks)
        }
        if (provider.minecraftDownloader.server) {
            applyServerRunConfig(tasks)
        }
    }

    protected open fun applyClientRunConfig(tasks: TaskContainer) {
        provider.provideVanillaRunClientTask(tasks) { }
    }

    protected open fun applyServerRunConfig(tasks: TaskContainer) {
        provider.provideVanillaRunServerTask(tasks)
    }

    @ApiStatus.Internal
    open fun afterEvaluate() {
        provider.parent.events.register(::sourceSets)
        provider.parent.events.register(::applyRunConfigs)
    }

    @ApiStatus.Internal
    open fun sourceSets(sourceSets: SourceSetContainer) {
    }

    @ApiStatus.Internal
    open fun afterRemap(envType: EnvType, namespace: String, baseMinecraft: Path): Path {
        return baseMinecraft
    }

    @ApiStatus.Internal
    open fun afterRemapJarTask(task: RemapJarTask, output: Path) {
        // do nothing
    }
}