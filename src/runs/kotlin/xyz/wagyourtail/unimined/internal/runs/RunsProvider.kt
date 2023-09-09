package xyz.wagyourtail.unimined.internal.runs

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.runs.RunsConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.sourceSets
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
            project.afterEvaluate {
                (transformers.keys - runConfigs.keys).apply {
                    if (isNotEmpty()) throw IllegalStateException("You have transformers for run configs that don't exist: $this")
                }
                project.logger.lifecycle("[Unimined/Runs] Applying runs")
                genIntellijRunsTask()
                createGradleRuns()
            }
        } else {
            project.afterEvaluate {
                if (project.tasks.findByName("genIntellijRuns") == null) {
                    project.tasks.register("genIntellijRuns") { task ->
                        task.group = "unimined_runs"
                        if (minecraft.sourceSet != project.sourceSets.getByName("main")) {
                            task.dependsOn(*project.unimined.minecrafts.keys.map {
                                "genIntellijRuns".withSourceSet(
                                    minecraft.sourceSet
                                )
                            }.mapNotNull { project.tasks.findByName(it) }.toTypedArray())
                        }
                    }
                }
            }
        }
    }

    private fun doGenIntellijRuns() {
        for (configName in runConfigs.keys) {
            if (transformedRunConfig[configName].disabled) continue
            project.logger.info("[Unimined/Runs] Creating idea run config for $configName")
            transformedRunConfig[configName].createIdeaRunConfig()
        }
    }

    private fun genIntellijRunsTask() {
        val genIntellijRuns = project.tasks.register("genIntellijRuns".withSourceSet(minecraft.sourceSet)) {
            if (minecraft.sourceSet == project.sourceSets.getByName("main")) {
                it.group = "unimined_runs"
                it.dependsOn(*project.unimined.minecrafts.keys.map { "genIntellijRuns".withSourceSet(minecraft.sourceSet) }.mapNotNull { project.tasks.findByName(it) }.toTypedArray())
            } else {
                it.group = "unimined_internal"
            }
            it.doLast {
                doGenIntellijRuns()
            }
        }
        project.afterEvaluate {
            if (java.lang.Boolean.getBoolean("idea.sync.active")) {
                doGenIntellijRuns()
            }
        }
    }

    private fun createGradleRuns() {
        for (configName in runConfigs.keys) {
            if (transformedRunConfig[configName].disabled) continue
            project.logger.info("[Unimined/Runs] Creating gradle task for $configName")
            transformedRunConfig[configName].createGradleTask(project.tasks, "unimined_runs")
        }
    }

}