package xyz.wagyourtail.unimined.internal.runs

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.runs.RunsConfig
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.withSourceSet

class RunsProvider(val project: Project, val minecraft: MinecraftConfig) : RunsConfig() {
    private var freeze = false

    private val runConfigs = mutableMapOf<String, RunConfig>()
    private val transformers = defaultedMapOf<String, RunConfig.() -> Unit> { {} }

    private val transformedRunConfig = defaultedMapOf<String, RunConfig>{
        if (!freeze) throw IllegalStateException("Cannot get transformed run configs before apply has been called.")
        runConfigs[it].apply { transformers[it].invoke(this!!) }!!
    }

    override fun config(config: String, action: RunConfig.() -> Unit) {
        if (freeze) throw IllegalStateException("Cannot register run configs after apply has been called.")
        transformers.compute(config) { _, prev ->
            {
                prev?.invoke(this)
                action.invoke(this)
            }
        }
    }

    override fun addTarget(config: RunConfig) {
        if (freeze) throw IllegalStateException("Cannot register run configs after apply has been called.")
        runConfigs[config.name] = config
    }

    override fun configFirst(config: String, action: RunConfig.() -> Unit) {
        if (freeze) throw IllegalStateException("Cannot register run configs after apply has been called.")
        transformers.compute(config) { _, prev ->
            {
                action.invoke(this)
                prev?.invoke(this)
            }
        }
    }

    fun apply() {
        freeze = true
        if (!off) {
            (transformers.keys-runConfigs.keys).apply {
                if (isNotEmpty()) throw IllegalStateException("You have transformers for run configs that don't exist: $this")
            }
            project.logger.lifecycle("[Unimined/Runs] Applying runs")
            genIntellijRunsTask()
            createGradleRuns()
        }
    }

    private fun genIntellijRunsTask() {
        val genIntellijRuns = project.tasks.register("genIntellijRuns".withSourceSet(minecraft.sourceSet)) {
            it.group = "unimined_runs"
            it.doLast {
                for (configName in runConfigs.keys) {
                    transformedRunConfig[configName].createIdeaRunConfig()
                }
            }
        }
//        project.tasks.named("idea").configure { it.finalizedBy(genIntellijRuns) }
    }

    private fun createGradleRuns() {
        for (configName in runConfigs.keys) {
            project.logger.info("[Unimined/Runs] Creating gradle task for $configName")
            transformedRunConfig[configName].createGradleTask(project.tasks, "unimined_runs")
        }
    }


}