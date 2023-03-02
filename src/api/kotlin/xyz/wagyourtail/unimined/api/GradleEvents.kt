package xyz.wagyourtail.unimined.api

import org.gradle.api.Project
import org.gradle.api.internal.lambdas.SerializableLambdas.action
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GradleEvents(val project: Project) {
    private val afterEvaluate = mutableListOf<() -> Unit>()
    private val sourceSets = mutableListOf<(SourceSetContainer) -> Unit>()
    private val tasks = mutableListOf<(TaskContainer) -> Unit>()

    private var lock = false
    private var sourceLock = false
    private var taskLock = false

    init {
        project.afterEvaluate {
            afterEvaluate()
        }
    }

    private fun afterEvaluate() {
        lock = true
        for (action in afterEvaluate) {
            action()
        }
        sourceLock = true
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        for (action in this.sourceSets) {
            action(sourceSets)
        }
        taskLock = true
        val tasks = project.tasks
        for (action in this.tasks) {
            action(tasks)
        }
    }

    fun register(action: () -> Unit) {
        if (lock) throw IllegalStateException("Cannot register afterEvaluate actions after afterEvaluate has been called.")
        afterEvaluate.add(action)
    }

    @JvmName("registerSourceSets")
    fun register(action: (SourceSetContainer) -> Unit) {
        if (sourceLock) throw IllegalStateException("Cannot register sourceSet actions after afterEvaluate has been called.")
        sourceSets.add(action)
    }

    @JvmName("registerTasks")
    fun register(action: (TaskContainer) -> Unit) {
        if (taskLock) throw IllegalStateException("Cannot register task actions after afterEvaluate has been called.")
        tasks.add(action)
    }
}