package xyz.wagyourtail.unimined.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer

class GradleEvents(project: Project) {
    private val afterEvaluate = mutableListOf<() -> Unit>()
    private val sourceSets = mutableListOf<(SourceSetContainer) -> Unit>()
    private val tasks = mutableListOf<(TaskContainer) -> Unit>()

    init {
        project.afterEvaluate {
            afterEvaluate.forEach { it() }
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            this.sourceSets.forEach { it(sourceSets) }
            this.tasks.forEach { it(project.tasks) }
        }
    }

    fun register(action: () -> Unit) {
        afterEvaluate.add(action)
    }

    @JvmName("registerSourceSets")
    fun register(action: (SourceSetContainer) -> Unit) {
        sourceSets.add(action)
    }

    @JvmName("registerTasks")
    fun register(action: (TaskContainer) -> Unit) {
        tasks.add(action)
    }
}