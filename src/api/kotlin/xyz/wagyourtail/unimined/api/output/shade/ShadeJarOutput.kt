package xyz.wagyourtail.unimined.api.output.shade

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.output.Output

/**
 * This is designed for shading other output provider results
 * @since 0.5.0
 */
interface ShadeJarOutput : Output<Jar>  {

    fun shadeFrom(project: Project, shadedStepName: String, named: String)
    fun shadeFromAll(project: Project, shadedStepName: String)

}