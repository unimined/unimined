package xyz.wagyourtail.unimined.output.remap

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.output.remap.RemapJarOutput
import xyz.wagyourtail.unimined.api.tasks.RemapJarTask
import xyz.wagyourtail.unimined.output.OutputImpl
import xyz.wagyourtail.unimined.output.OutputProviderImpl
import xyz.wagyourtail.unimined.output.jar.JarOutputImpl

class RemapJarOutputImpl(
    project: Project,
    unimined: UniminedExtension,
    parent: OutputProviderImpl
) : OutputImpl<RemapJarTask, Jar>(project, unimined, parent, "remapJar"), RemapJarOutput {

    override fun applyEnvConfig(env: EnvType, task: RemapJarTask) {
        task.envType.set(env)
    }

    override fun create(name: String): RemapJarTask {
        return project.tasks.create(name, RemapJarTaskImpl::class.java)
    }

    override fun beforeResolvePrev() {
        // add -dev to indicate that those jars are not remapped
        prev!!.configAllFirst {
            if (archiveClassifier.isPresent && archiveClassifier.get().isNotEmpty()) {
                archiveClassifier.set("${archiveClassifier.get()}-dev")
            } else {
                archiveClassifier.set("dev")
            }
        }
    }
    override fun beforeResolve(prev: Map<String, Jar>?) {
        configAll {
            inputFile.set(prev!![it]!!.archiveFile)
        }
    }

}
