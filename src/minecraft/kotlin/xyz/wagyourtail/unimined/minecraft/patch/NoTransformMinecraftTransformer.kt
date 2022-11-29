package xyz.wagyourtail.unimined.minecraft.patch

import org.gradle.api.Project
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl

class NoTransformMinecraftTransformer(project: Project, provider: MinecraftProviderImpl) : xyz.wagyourtail.unimined.minecraft.patch.AbstractMinecraftTransformer(
    project,
    provider
) {

    override val prodNamespace: String
        get() = "official"

    override var devNamespace: String = "named"
    override var devFallbackNamespace: String = "intermediary"

    override fun transform(minecraft: xyz.wagyourtail.unimined.minecraft.patch.MinecraftJar): xyz.wagyourtail.unimined.minecraft.patch.MinecraftJar {
        return minecraft
    }

}