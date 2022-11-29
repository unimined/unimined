package xyz.wagyourtail.unimined.minecraft.mod

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.mod.ModProvider

class ModProviderImpl(
    val project: Project,
    parent: UniminedExtension
) : ModProvider(parent) {

    override val modRemapper = ModRemapperImpl(project, this)

    init {
        parent.events.register(::afterEvaluate)
    }

    private fun afterEvaluate() {
        for (envType in EnvType.values()) {
            if (envType == EnvType.COMBINED && parent.minecraftProvider.disableCombined.get()) continue
            modRemapper.remap(envType)
        }
    }
}