package xyz.wagyourtail.unimined.internal.mods

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.mod.ModRemapSettings
import xyz.wagyourtail.unimined.api.mod.ModsConfig

class ModsProvider(val project: Project, val minecraft: MinecraftConfig) : ModsConfig() {

    private val remapConfigs = mutableMapOf<Configuration, ModRemapSettings.() -> Unit>()

    override fun remap(config: Configuration, action: ModRemapSettings.() -> Unit) {
        TODO("Not yet implemented")
    }

    fun apply() {
        TODO("IMPLEMENT")
    }


}
