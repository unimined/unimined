package xyz.wagyourtail.unimined.output.remap

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.output.remap.RemapJarOutput
import xyz.wagyourtail.unimined.api.tasks.RemapJarTask
import xyz.wagyourtail.unimined.output.OutputImpl

class RemapJarOutputImpl(
    project: Project,
    prev: OutputImpl<*>?
) : OutputImpl<RemapJarTask>(project, prev, "remapJar"), RemapJarOutput {

    override fun config(named: String, apply: RemapJarTask.() -> Unit) {
        TODO("Not yet implemented")
    }

    override fun applyEnvConfig(env: EnvType, task: RemapJarTask) {
        TODO("Not yet implemented")
    }

}
