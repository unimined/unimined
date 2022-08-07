package xyz.wagyourtail.unimined2

import org.gradle.api.Plugin
import org.gradle.api.Project
import xyz.wagyourtail.unimined.maybeCreate
import xyz.wagyourtail.unimined2.minecraft.MinecraftProvider
import java.nio.file.Path

class UniminedPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply(mapOf(
            "plugin" to "java"
        ))

        val ext = project.extensions.create("unimined", UniminedExtension::class.java, project)
        project.extensions.create("unimined", MinecraftProvider::class.java, project, ext)
    }


}
