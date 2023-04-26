package xyz.wagyourtail.unimined.output

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.output.Output
import xyz.wagyourtail.unimined.api.output.OutputProvider
import xyz.wagyourtail.unimined.api.output.shade.ShadeJarOutput
import xyz.wagyourtail.unimined.output.jar.JarOutputImpl
import xyz.wagyourtail.unimined.output.remap.RemapJarOutputImpl
import xyz.wagyourtail.unimined.output.shade.ShadeJarOutputImpl

open class OutputProviderImpl(
    val project: Project,
    val unimined: UniminedExtension
) : OutputProvider() {

    val sequence: MutableList<OutputImpl<*, *>> = mutableListOf()

    override val jar = JarOutputImpl(project, unimined, this).also { sequence.add(it) }

    override val remapJar = RemapJarOutputImpl(project, unimined, this).also { sequence.add(it) }

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
                // no-op
            }

            override fun create(name: String): T {
                return project.tasks.create(name, type)
            }
        }
    }

    override fun <T: Jar> addStep(name: String, type: Class<T>): OutputImpl<T, *> {
        return outputStep(name, type).also { sequence.add(it) }
    }

    override fun <T: Jar> addStep(name: String, type: Class<T>, action: Output<T>.() -> Unit) {
        val output = outputStep(name, type)
        sequence.add(output)
        output.action()
    }

    override fun <T: Jar> addStepBefore(before: String, name: String, type: Class<T>): OutputImpl<T, *> {
        if (isResolved()) {
            throw IllegalStateException("cannot add step after resolve")
        }
        val output = outputStep(name, type)
        sequence.add(sequence.indexOfFirst { it.baseTaskName == before }.also {
            if (it == -1) {
                throw IllegalArgumentException("before task $before not found")
            }
         }, output)
        return output
    }

    override fun <T: Jar> addStepBefore(before: String, name: String, type: Class<T>, action: Output<T>.() -> Unit) {
        if (isResolved()) {
            throw IllegalStateException("cannot add step after resolve")
        }
        val output = outputStep(name, type)
        sequence.add(sequence.indexOfFirst { it.baseTaskName == before }.also {
            if (it == -1) {
                throw IllegalArgumentException("before task $before not found")
            }
        }, output)
        output.action()
    }

    override fun addShadeStep(name: String, action: ShadeJarOutput.() -> Unit) {
        val shadeStep = ShadeJarOutputImpl<Jar>(project, unimined, this, name)
        sequence.add(shadeStep)
        shadeStep.action()
    }

    override fun addShadeStepBefore(before: String, name: String, action: ShadeJarOutput.() -> Unit) {
        if (isResolved()) {
            throw IllegalStateException("cannot add step after resolve")
        }
        val shadeStep = ShadeJarOutputImpl<Jar>(project, unimined, this, name)
        sequence.add(sequence.indexOfFirst { it.baseTaskName == before }.also {
            if (it == -1) {
                throw IllegalArgumentException("before task $before not found")
            }
        }, shadeStep)
        shadeStep.action()
    }

    override fun getStep(name: String): OutputImpl<*, *>? = sequence.find { it.baseTaskName == name }

    override fun getStep(name: String, action: Output<*>.() -> Unit) {
        getStep(name)!!.also(action)
    }

    init {
        unimined.events.register(::afterEvaluate)
    }

    fun resolve() = sequence.last().resolve()

    fun isResolved() = sequence.last().isResolved()

    private fun afterEvaluate() {
        // only resolve last, it will chain backwards
        for (task in resolve().values) {
            buildTask.dependsOn(task)
        }
    }

    override fun removeStep(name: String) {
        val step = getStep(name)!!
        if (step.isResolved()) {
            throw IllegalStateException("cannot remove after resolving outputs")
        }
        sequence.remove(step)
    }

}
