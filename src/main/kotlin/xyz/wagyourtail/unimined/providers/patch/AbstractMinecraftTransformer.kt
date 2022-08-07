package xyz.wagyourtail.unimined.providers.patch

import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import java.nio.file.Path

abstract class AbstractMinecraftTransformer protected constructor(
    protected val project: Project,
    val provider: MinecraftProvider
) {

    abstract fun transformClient(baseMinecraft: Path): Path
    abstract fun transformServer(baseMinecraft: Path): Path
    abstract fun transformCombined(baseMinecraft: Path): Path

    fun applyRunConfigs() {
        if (provider.minecraftDownloader.client) {
            applyClientRunConfig()
        }
        if (provider.minecraftDownloader.server) {
            applyServerRunConfig()
        }
    }

    protected open fun applyClientRunConfig() {
        provider.provideRunClientTask {  }
    }

    protected open fun applyServerRunConfig() {
        provider.provideRunServerTask {  }
    }

    open fun init() = Unit
}