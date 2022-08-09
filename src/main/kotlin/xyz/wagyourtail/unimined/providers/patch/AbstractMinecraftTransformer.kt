package xyz.wagyourtail.unimined.providers.patch

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import java.nio.file.Path

abstract class AbstractMinecraftTransformer protected constructor(
    protected val project: Project,
    val provider: MinecraftProvider
) {

    init {
        provider.parent.events.register(::sourceSets)
        provider.parent.events.register(::applyRunConfigs)
    }

    abstract fun transformClient(baseMinecraft: Path): Path
    abstract fun transformServer(baseMinecraft: Path): Path
    abstract fun transformCombined(baseMinecraft: Path): Path

    private fun applyRunConfigs(tasks: TaskContainer) {
        if (provider.minecraftDownloader.client) {
            applyClientRunConfig(tasks)
        }
        if (provider.minecraftDownloader.server) {
            applyServerRunConfig(tasks)
        }
    }

    protected open fun applyClientRunConfig(tasks: TaskContainer) {
        provider.provideRunClientTask(tasks) {  }
    }

    protected open fun applyServerRunConfig(tasks: TaskContainer) {
        provider.provideRunServerTask(tasks) {  }
    }

    open fun afterEvaluate() {}
    open fun sourceSets(sourceSets: SourceSetContainer) {}
}