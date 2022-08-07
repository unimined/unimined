package xyz.wagyourtail.unimined.providers.patch

import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import java.io.File

abstract class AbstractMinecraftTransformer protected constructor(
    protected val project: Project,
    protected val provider: MinecraftProvider
) {

    abstract fun transform(artifact: ArtifactIdentifier, file: File): File

    fun applyRunConfigs() {
        if (MinecraftProvider.getMinecraftDownloader(project).client) {
            applyClientRunConfig()
        }
        if (MinecraftProvider.getMinecraftDownloader(project).server) {
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