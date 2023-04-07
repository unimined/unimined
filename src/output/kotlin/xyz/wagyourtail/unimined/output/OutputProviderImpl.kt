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

    //TODO: document how to use this for shadow
    override fun addOutputStep(name: String, type: Class<out Jar>): OutputImpl<*, *> {
        return object : OutputImpl<Jar, Jar>(project, unimined, sequence.last() as OutputImpl<Jar, *>, name) {
            override fun applyEnvConfig(env: EnvType, task: Jar) {
            }

            override fun create(name: String, config: Jar.() -> Unit): Jar {
                return project.tasks.create(name, type, config)
            }
        }.also { sequence.add(it) }
    }

    override fun getOutputStep(name: String): OutputImpl<*, *>? = sequence.find { it.baseTaskName == name }

    init {
        unimined.events.register(::afterEvaluate)
    }

    private fun afterEvaluate() {
        // only resolve last, it will chain backwards
        sequence.last().resolve()
    }

}
