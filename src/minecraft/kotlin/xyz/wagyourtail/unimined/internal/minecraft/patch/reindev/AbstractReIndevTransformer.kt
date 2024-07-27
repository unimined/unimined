package xyz.wagyourtail.unimined.internal.minecraft.patch.reindev

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.resolver.Library
import xyz.wagyourtail.unimined.util.SemVerUtils

abstract class AbstractReIndevTransformer(
    project: Project,
    provider: MinecraftProvider,
    providerName: String
) : AbstractMinecraftTransformer(project, provider, providerName) {

    override var canCombine: Boolean = SemVerUtils.matches(provider.version.replace("_", "."), ">2.9")

    init {
        addMavens()
    }

    fun addMavens() {
        project.unimined.fox2codeMaven()
    }
}
