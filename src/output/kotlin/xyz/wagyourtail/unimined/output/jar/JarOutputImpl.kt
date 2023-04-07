package xyz.wagyourtail.unimined.output.jar

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.output.jar.JarOutput
import xyz.wagyourtail.unimined.output.OutputImpl

class JarOutputImpl(
    project: Project,
    unimined: UniminedExtension
) : OutputImpl<Jar, Nothing>(project, unimined, null, "jar"), JarOutput {

    override fun afterEvaluate() {
        super.afterEvaluate()
        // remove default jar task
        val jarTask = project.tasks.getByName("jar")
        jarTask.enabled = false
    }

    override fun create(name: String): Jar {
        return project.tasks.create(name, Jar::class.java)
    }

    override fun applyEnvConfig(env: EnvType, task: Jar) {
        task.archiveClassifier.set(env.classifier)
        task.from(*(when (env) {
            EnvType.COMBINED -> {
                project.minecraft.combinedSourceSets
            }
            EnvType.CLIENT -> {
                (project.minecraft.clientSourceSets + project.minecraft.combinedSourceSets).toSet()
            }
            EnvType.SERVER -> {
                (project.minecraft.serverSourceSets + project.minecraft.combinedSourceSets).toSet()
            }
        }.map {
            it.output
        }.also {
            project.logger.lifecycle("Adding source set output: $it")
        }).toTypedArray())
    }
}
