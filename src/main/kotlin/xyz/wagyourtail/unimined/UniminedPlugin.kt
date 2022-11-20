package xyz.wagyourtail.unimined

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.remap.RemapJarTask

@Suppress("UNUSED")
class UniminedPlugin : Plugin<Project> {
    lateinit var ext: UniminedExtension

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

        ext = project.extensions.create("unimined", UniminedExtension::class.java, project)
        remapJarTask(project, project.tasks)
    }

    fun remapJarTask(project: Project, tasks: TaskContainer) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val jarTask = tasks.getByName("jar") as Jar
        val client = sourceSets.findByName("client")
        val server = sourceSets.findByName("server")
        val build = tasks.getByName("build")
        if (client != null || server != null) {
            jarTask.archiveClassifier.set("client-dev")
            client?.output?.let { out -> jarTask.from(out) }
        } else {
            jarTask.archiveClassifier.set("dev")
        }
        val remapJar = tasks.register("remapJar", RemapJarTask::class.java) {
            it.dependsOn(jarTask)
            it.group = "unimined"
            it.inputFile.convention(jarTask.archiveFile)
            if (client != null) {
                it.archiveClassifier.set("client")
            }
            it.envType.set(EnvType.CLIENT)
        }
        if (server != null || client != null) {
            val serverJar = tasks.register("serverJar", Jar::class.java) {
                server?.output?.let { out -> it.from(out) }
                it.group = "unimined"
                it.archiveClassifier.set("server-dev")
            }.get()
            val serverRemapJar = tasks.register("serverRemapJar", RemapJarTask::class.java) {
                it.dependsOn(serverJar)
                it.group = "unimined"
                it.inputFile.convention(serverJar.archiveFile)
                it.archiveClassifier.set("server")
                it.envType.set(EnvType.SERVER)
            }
            build.dependsOn(serverRemapJar)
        }
        build.dependsOn(remapJar)
    }
}
