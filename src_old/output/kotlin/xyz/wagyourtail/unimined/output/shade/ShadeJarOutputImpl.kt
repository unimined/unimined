package xyz.wagyourtail.unimined.output.shade

import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.output.outputProvider
import xyz.wagyourtail.unimined.api.output.shade.ShadeJarOutput
import xyz.wagyourtail.unimined.output.OutputImpl
import xyz.wagyourtail.unimined.output.OutputProviderImpl

class ShadeJarOutputImpl<U: Jar>(
    project: Project,
    unimined: UniminedExtension,
    parent: OutputProviderImpl,
    baseTaskName: String
) : OutputImpl<Jar, U>(project, unimined, parent, baseTaskName), ShadeJarOutput {

    override fun shadeFrom(project: Project, shadedStepName: String, named: String) {
        (project.outputProvider as OutputProviderImpl).resolve()
        val shade = project.outputProvider.getStep(shadedStepName)!! as OutputImpl<*,*>
        config(named) {
            from(project.zipTree((project.task("$shadedStepName${named.capitalized()}") as Jar).archiveFile))
        }
    }

    override fun shadeFromAll(project: Project, shadedStepName: String) {
        (project.outputProvider as OutputProviderImpl).resolve()
        val shade = project.outputProvider.getStep(shadedStepName)!! as OutputImpl<*,*>
        configAll {
            from(project.zipTree((project.task("$shadedStepName${it.capitalized()}") as Jar).archiveFile))
        }
    }

    override fun applyEnvConfig(env: EnvType, task: Jar) {
        // no-op
    }

    override fun create(name: String): Jar {
        return project.tasks.create(name, Jar::class.java)
    }


}