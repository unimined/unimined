package xyz.wagyourtail.unimined.internal.mods

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.mod.ModRemapConfig
import xyz.wagyourtail.unimined.api.mod.ModsConfig
import xyz.wagyourtail.unimined.util.withSourceSet

class ModsProvider(val project: Project, val minecraft: MinecraftConfig) : ModsConfig() {

    private val remapConfigs = mutableMapOf<Set<Configuration>, ModRemapProvider.() -> Unit>()

    private val modImplementation = project.configurations.maybeCreate("modImplementation".withSourceSet(minecraft.sourceSet)).also {
        project.configurations.getByName("implementation".withSourceSet(minecraft.sourceSet)).extendsFrom(it)
        remap(it)
    }

    init {
        project.afterEvaluate {
            afterEvaluate()
        }
        project.repositories.flatDir {

        }
    }

    override fun remap(config: List<Configuration>, action: ModRemapConfig.() -> Unit) {
        remapConfigs[config.toSet()] = action
    }

    fun afterEvaluate() {
        for ((config, action) in remapConfigs) {
            val remapSettings = ModRemapProvider(config, project, minecraft)
            remapSettings.action()
            remapSettings.doRemap()
        }
    }

}
