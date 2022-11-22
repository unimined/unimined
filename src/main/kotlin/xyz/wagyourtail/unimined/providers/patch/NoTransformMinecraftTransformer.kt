package xyz.wagyourtail.unimined.providers.patch

import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.MinecraftProvider

class NoTransformMinecraftTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(
    project,
    provider
) {

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        return minecraft
    }

}