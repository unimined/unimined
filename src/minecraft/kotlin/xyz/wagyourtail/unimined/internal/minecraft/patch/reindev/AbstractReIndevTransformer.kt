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

    init {
        project.unimined.fox2codeMaven()
    }

    /**
     * Strips the revision number before comparing the version. Technically only the major and minor is needed.
     */
    override var canCombine: Boolean = SemVerUtils.matches(provider.version.replace(Regex("_[0-9]*$"), ""), ">=2.9")

}
