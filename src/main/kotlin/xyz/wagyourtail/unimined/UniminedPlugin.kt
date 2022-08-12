package xyz.wagyourtail.unimined

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.remap.RemapJarTask

@Suppress("UNUSED")
class UniminedPlugin : Plugin<Project> {
    lateinit var ext: UniminedExtension
    lateinit var minecraftProvider: MinecraftProvider

    lateinit var sourceSets: SourceSetContainer

    override fun apply(project: Project) {
        project.apply(mapOf(
            "plugin" to "java"
        ))
        project.apply(mapOf(
            "plugin" to "idea"
        ))

        sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        ext = project.extensions.create("unimined", UniminedExtension::class.java, project)
        minecraftProvider = project.extensions.create("minecraft", MinecraftProvider::class.java, project, ext)
        remapJarTask(project.tasks)
    }

    fun remapJarTask(tasks: TaskContainer) {
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
            }
            build.dependsOn(serverRemapJar)
        }
        build.dependsOn(remapJar)
    }
}
