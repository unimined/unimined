package xyz.wagyourtail.unimined.output

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.output.OutputProvider
import xyz.wagyourtail.unimined.output.jar.JarOutputImpl
import xyz.wagyourtail.unimined.output.remap.RemapJarOutputImpl

class OutputProviderImpl(
    val project: Project
) : OutputProvider() {

    override val jar = JarOutputImpl(project)

    override val remapJar = RemapJarOutputImpl(project, jar)

}
