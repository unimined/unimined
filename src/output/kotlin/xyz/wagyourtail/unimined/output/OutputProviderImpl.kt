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

    fun getBefore(output: OutputImpl<*, *>): OutputImpl<*, *>? {
        val index = sequence.indexOf(output)
        if (index == -1) {
            throw IllegalArgumentException("output not found")
        }
        return sequence.getOrNull(index - 1)
    }

    private fun <T: Jar> outputStep(name: String, type: Class<T>): OutputImpl<T, *> {
        return object : OutputImpl<T, Jar>(project, unimined, this, name) {
            override fun applyEnvConfig(env: EnvType, task: T) {
            }

            override fun create(name: String): T {
                return project.tasks.create(name, type)
            }
        }
    }

    override fun <T: Jar> addOutputStep(name: String, type: Class<T>): OutputImpl<T, *> {
        return outputStep(name, type).also { sequence.add(it) }
    }

    override fun <T: Jar> addOutputStepBefore(name: String, type: Class<T>, before: String): OutputImpl<T, *> {
        val output = outputStep(name, type)
        sequence.add(sequence.indexOfFirst { it.baseTaskName == before }.also {
            if (it == -1) {
                throw IllegalArgumentException("before task $before not found")
            }
         }, output)
        return output
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
