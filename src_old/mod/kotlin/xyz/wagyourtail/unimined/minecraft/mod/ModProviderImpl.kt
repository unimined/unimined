package xyz.wagyourtail.unimined.minecraft.mod

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.mod.ModProvider

@Suppress("LeakingThis")
open class ModProviderImpl(
    val project: Project,
    uniminedExtension: UniminedExtension
): ModProvider(uniminedExtension) {

    override val modRemapper = ModRemapperImpl(project, this, uniminedExtension)

    override val combinedConfig = Configs(project, EnvType.COMBINED, uniminedExtension)
    override val clientConfig = Configs(project, EnvType.CLIENT, uniminedExtension)
    override val serverConfig = Configs(project, EnvType.SERVER, uniminedExtension)

    init {
        uniminedExtension.events.register(::afterEvaluate)
    }

    private fun afterEvaluate() {
        for (envType in EnvType.values()) {
            if (envType == EnvType.COMBINED && project.minecraft.disableCombined.get()) continue
            modRemapper.remap(envType)
        }
    }
}