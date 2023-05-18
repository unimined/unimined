package xyz.wagyourtail.unimined

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.internal.minecraft.task.GenSourcesTaskImpl

@Suppress("UNUSED")
class UniminedPlugin: Plugin<Project> {
    lateinit var ext: UniminedExtensionImpl

    override fun apply(project: Project) {
        project.apply(
            mapOf(
                "plugin" to "java"
            )
        )
        project.apply(
            mapOf(
                "plugin" to "idea"
            )
        )

        ext = project.extensions.create("unimined", UniminedExtensionImpl::class.java, project)
        genSourcesTask(project.tasks)
    }

    private fun genSourcesTask(tasks: TaskContainer) {
        tasks.register("genSources", GenSourcesTaskImpl::class.java) {
            it.group = "unimined"
        }
    }
}
