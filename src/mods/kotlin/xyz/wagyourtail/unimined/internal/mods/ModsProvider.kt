package xyz.wagyourtail.unimined.internal.mods

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.mod.ModRemapSettings
import xyz.wagyourtail.unimined.api.mod.ModsConfig

class ModsProvider(val project: Project, val minecraft: MinecraftConfig) : ModsConfig() {

    private val remapConfigs = mutableMapOf<Set<Configuration>, ModConfig.() -> Unit>()

    init {
        project.afterEvaluate {
            afterEvaluate()
        }
    }

    override fun remap(config: List<Configuration>, action: ModRemapSettings.() -> Unit) {
        remapConfigs[config.toSet()] = action
    }

    fun afterEvaluate() {
        for ((config, action) in remapConfigs) {
            val remapSettings = ModConfig(project, minecraft)
            remapSettings.action()
            remapSettings.doRemap(config)
        }
    }

}
