package xyz.wagyourtail.unimined

import org.gradle.api.Plugin
import org.gradle.api.Project
import xyz.wagyourtail.unimined.internal.minecraft.task.RemapSourcesJarTaskImpl
import xyz.wagyourtail.unimined.util.sourceSets

class UniminedPlugin: Plugin<Project> {

    val pluginVersion: String = UniminedPlugin::class.java.`package`.implementationVersion ?: "unknown"

    val Long.kb: Long
        get() = this * 1024L

    val Long.mb: Long
        get() = this.kb * 1024L

    val Long.gb: Long
        get() = this.mb * 1024L

    override fun apply(project: Project) {
        project.logger.lifecycle("[Unimined] Plugin Version: $pluginVersion")

        if (Runtime.getRuntime().maxMemory() < 2L.gb) {
            project.logger.warn("")
            project.logger.warn("[Unimined] You have less than 2GB of memory allocated to gradle.")
            project.logger.warn("[Unimined] This may cause issues with remapping and other tasks.")
            project.logger.warn("[Unimined] Please allocate more memory to gradle by adding: ")
            project.logger.warn("[Unimined]   org.gradle.jvmargs=-Xmx2G")
            project.logger.warn("[Unimined] to your gradle.properties file.")
            project.logger.warn("")
        }

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

        project.afterEvaluate {
            it.sourceSets.forEach { s ->
                RemapSourcesJarTaskImpl.setup(s, it)
            }
        }
    }

}