package xyz.wagyourtail.unimined.internal.minecraft.patch.reindev

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.util.compareFlexVer

abstract class AbstractReIndevTransformer(
    project: Project,
    provider: MinecraftProvider,
    providerName: String
) : AbstractMinecraftTransformer(project, provider, providerName) {

    init {
        project.unimined.fox2codeMaven()
    }

    override var canCombine: Boolean = provider.version.compareFlexVer("2.9") >= 0

}
