package xyz.wagyourtail.unimined.output.jar

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.output.jar.JarOutput
import xyz.wagyourtail.unimined.output.OutputImpl

class JarOutputImpl(
    project: Project
) : OutputImpl<Jar>(project, null, "jar"), JarOutput {

    override fun afterEvaluate() {
        super.afterEvaluate()
        // remove default jar task
        val jarTask = project.tasks.getByName("jar")
        project.tasks.remove(jarTask)
//        buildTask.dependsOn.remove(jarTask)
    }

    override fun config(named: String, apply: Jar.() -> Unit) {
        TODO("Not yet implemented")
    }

    override fun applyEnvConfig(env: EnvType, task: Jar) {
        TODO("Not yet implemented")
    }

}
