package xyz.wagyourtail.unimined.providers.patch

import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.MinecraftProviderImpl

class NoTransformMinecraftTransformer(project: Project, provider: MinecraftProviderImpl) : AbstractMinecraftTransformer(
    project,
    provider
) {

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        return minecraft
    }

}