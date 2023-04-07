package xyz.wagyourtail.unimined.api.output

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.output.jar.JarOutput
import xyz.wagyourtail.unimined.api.output.remap.RemapJarOutput


val Project.outputProvider
    get() = extensions.getByType(OutputProvider::class.java)


/**
 * @since 0.5.0
 */
abstract class OutputProvider {

    abstract val jar: JarOutput

    abstract val remapJar: RemapJarOutput

    abstract fun addOutputStep(name: String, type: Class<out Jar>): Output<*>
    abstract fun getOutputStep(name: String): Output<*>?
}
