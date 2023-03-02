package xyz.wagyourtail.unimined.launch

import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.launch.LaunchConfig
import xyz.wagyourtail.unimined.api.launch.LauncherProvider
import xyz.wagyourtail.unimined.api.unimined

class LauncherProvierImpl(project: Project, unimined: UniminedExtension) : LauncherProvider() {

    private var lock = false

    init {
        unimined.events.register { task: TaskContainer ->
            lock = true
            if (!off) {
                genIntellijRunsTask(task)
                createGradleRuns(task)
            }
        }
    }

    private val launchTargets = mutableMapOf<String, LaunchConfig>()

    override fun config(config: String, action: LaunchConfig.() -> Unit) {
        if (lock) throw IllegalStateException("Cannot register launch targets after afterEvaluate has been called.")
        transformers.compute(config) { _, prev ->
            {
                prev?.invoke(this)
                action.invoke(this)
            }
        }
    }

    override fun addTarget(config: LaunchConfig) {
        if (lock) throw IllegalStateException("Cannot register launch targets after afterEvaluate has been called.")
        launchTargets[config.name] = config
    }

    private fun getTarget(targetName: String): LaunchConfig {
        return transform(launchTargets[targetName] ?: error("No target named $targetName"))
    }

    private fun transform(config: LaunchConfig): LaunchConfig {
        val conf = config.copy()
        transformers[config.name]?.invoke(conf)
        return conf
    }

    private fun genIntellijRunsTask(tasks: TaskContainer) {
        val genIntellijRuns = tasks.register("genIntellijRuns") {
            it.group = "unimined"
            it.doLast {
                for (configName in launchTargets.keys) {
                    getTarget(configName).createIdeaRunConfig()
                }
            }
        }
    }

    private fun createGradleRuns(tasks: TaskContainer) {
        for (configName in launchTargets.keys) {
            getTarget(configName).createGradleTask(tasks, "unimined_launcher")
        }
        if (!launchTargets.keys.containsAll(transformers.keys)) {
            throw IllegalStateException("You have transformers for targets that don't exist: ${transformers.keys - launchTargets.keys}")
        }
    }
}