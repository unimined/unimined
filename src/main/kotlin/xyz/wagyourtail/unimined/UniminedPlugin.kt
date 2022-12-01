package xyz.wagyourtail.unimined

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.remap.RemapJarTaskImpl

@Suppress("UNUSED")
class UniminedPlugin : Plugin<Project> {
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
        remapJarTask(project, project.tasks)
        genIntellijRunsTask(project, project.tasks)
    }

    private fun remapJarTask(project: Project, tasks: TaskContainer) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val jarTask = tasks.getByName("jar") as Jar
        val client = ext.minecraftProvider.clientSourceSets.firstOrNull()
        val server = ext.minecraftProvider.serverSourceSets.firstOrNull()
        val build = tasks.getByName("build")
        if (client != null || server != null) {
            jarTask.archiveClassifier.set("client-dev")
            client?.output?.let { out -> jarTask.from(out) }
        } else {
            jarTask.archiveClassifier.set("dev")
        }
        val remapJar = tasks.register("remapJar", RemapJarTaskImpl::class.java) {
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
            val serverRemapJar = tasks.register("serverRemapJar", RemapJarTaskImpl::class.java) {
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

    private fun genIntellijRunsTask(project: Project, tasks: TaskContainer) {
        val genIntellijRuns = tasks.register("genIntellijRuns") {
            it.group = "unimined"
            it.doLast {
                for (config in ext.minecraftProvider.runConfigs) {
                    config.createIdeaRunConfig()
                }
            }
        }
        tasks.findByName("idea")?.dependsOn(genIntellijRuns)
    }
}
