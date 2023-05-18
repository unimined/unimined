package xyz.wagyourtail.unimined.internal.runs

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.runs.RunsConfig

class RunsProvider(val project: Project, val minecraft: MinecraftConfig) : RunsConfig() {
    private var freeze = false


    private val runConfigs = mutableMapOf<String, RunConfig>()

    override fun config(config: String, action: RunConfig.() -> Unit) {
        TODO("Not yet implemented")
    }

    override fun addTarget(config: RunConfig) {
        TODO("Not yet implemented")
    }

    override fun configFirst(config: String, action: RunConfig.() -> Unit) {
        TODO("Not yet implemented")
    }

    fun apply() {

    }


}