package xyz.wagyourtail.unimined.providers.mod

import org.gradle.api.Project
import xyz.wagyourtail.unimined.UniminedExtensionImpl
import xyz.wagyourtail.unimined.providers.minecraft.EnvType

class ModProviderImpl(
    val project: Project,
    val parent: UniminedExtensionImpl
) {
    val modRemapper = ModRemapper(project, this)

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