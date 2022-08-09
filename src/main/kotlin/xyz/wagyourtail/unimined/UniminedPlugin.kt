package xyz.wagyourtail.unimined

import org.gradle.api.Plugin
import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider

class UniminedPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply(mapOf(
            "plugin" to "java"
        ))
        project.apply(mapOf(
            "plugin" to "idea"
        ))

        val ext = project.extensions.create("unimined", UniminedExtension::class.java, project)
        project.extensions.create("minecraft", MinecraftProvider::class.java, project, ext)
    }


}
