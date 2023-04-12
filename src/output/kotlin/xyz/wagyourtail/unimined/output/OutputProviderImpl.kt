package xyz.wagyourtail.unimined.output

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.output.OutputProvider
import xyz.wagyourtail.unimined.output.jar.JarOutputImpl
import xyz.wagyourtail.unimined.output.remap.RemapJarOutputImpl

open class OutputProviderImpl(
    val project: Project,
    val unimined: UniminedExtension
) : OutputProvider() {

    val sequence: MutableList<OutputImpl<*, *>> = mutableListOf()

    override val jar = JarOutputImpl(project, unimined).also { sequence.add(it) }

    override val remapJar = RemapJarOutputImpl(project, unimined, jar).also { sequence.add(it) }

    protected val buildTask by lazy {
        project.tasks.getByName("build")
    }

    //TODO: document how to use this for shadow
    override fun addOutputStep(name: String, type: Class<out Jar>): OutputImpl<*, *> {
        return object : OutputImpl<Jar, Jar>(project, unimined, sequence.last() as OutputImpl<Jar, *>, name) {
            override fun applyEnvConfig(env: EnvType, task: Jar) {
            }

            override fun create(name: String): Jar {
                return project.tasks.create(name, type)
            }
        }.also { sequence.add(it) }
    }

    override fun getOutputStep(name: String): OutputImpl<*, *>? = sequence.find { it.baseTaskName == name }

    init {
        unimined.events.register(::afterEvaluate)
    }

    private fun afterEvaluate() {
        // only resolve last, it will chain backwards
        for (task in sequence.last().resolve().values) {
            buildTask.dependsOn(task)
        }
    }

}
