package xyz.wagyourtail.unimined.minecraft.mod

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.mod.ModProvider
import xyz.wagyourtail.unimined.api.unimined

class ModProviderImpl(
    val project: Project
) : ModProvider() {

    override val modRemapper = ModRemapperImpl(project, this)

    override val combinedConfig = Configs(project, EnvType.COMBINED)
    override val clientConfig = Configs(project, EnvType.CLIENT)
    override val serverConfig = Configs(project, EnvType.SERVER)

    init {
        project.unimined.events.register(::afterEvaluate)
    }

    private fun afterEvaluate() {
        for (envType in EnvType.values()) {
<<<<<<< Updated upstream
            if (envType == EnvType.COMBINED && (parent.minecraftProvider.disableCombined.get() || parent.minecraftProvider.combinedSourceSets.isEmpty())) continue
=======
            if (envType == EnvType.COMBINED && project.minecraft.disableCombined.get()) continue
>>>>>>> Stashed changes
            modRemapper.remap(envType)
        }
    }
}