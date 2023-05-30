package xyz.wagyourtail.unimined

import org.gradle.api.Plugin
import org.gradle.api.Project

class UniminedPlugin: Plugin<Project> {

    val pluginVersion: String = UniminedPlugin::class.java.`package`.implementationVersion ?: "unknown"

    override fun apply(project: Project) {
        project.logger.lifecycle("[Unimined] Plugin Version: $pluginVersion")

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

        project.extensions.create("unimined", UniminedExtensionImpl::class.java, project)
    }

}