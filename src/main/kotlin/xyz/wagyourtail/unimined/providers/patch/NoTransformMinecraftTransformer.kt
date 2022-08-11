package xyz.wagyourtail.unimined.providers.patch

import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import java.nio.file.Path

class NoTransformMinecraftTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(project, provider) {

    override fun transform(envType: EnvType, baseMinecraft: Path): Path {
        return baseMinecraft
    }

}