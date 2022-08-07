package xyz.wagyourtail.unimined.providers.patch

import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import java.nio.file.Path

class NoTransformMinecraftTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(project, provider) {

    override fun transformClient(baseMinecraft: Path): Path {
        return baseMinecraft
    }

    override fun transformServer(baseMinecraft: Path): Path {
        return baseMinecraft
    }

    override fun transformCombined(baseMinecraft: Path): Path {
        return baseMinecraft
    }

}