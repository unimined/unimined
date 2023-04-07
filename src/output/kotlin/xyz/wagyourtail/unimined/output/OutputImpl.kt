package xyz.wagyourtail.unimined.output

import org.gradle.api.Project
import org.gradle.api.Task
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.output.Output
import xyz.wagyourtail.unimined.api.unimined

abstract class OutputImpl<T: Task>(
    val project: Project,
    val prev: OutputImpl<*>?,
    val baseTaskName: String
) : Output<T> {

    override var disable: Boolean = false

    protected val buildTask by lazy {
        project.tasks.getByName("build")
    }

    init {
        @Suppress("USELESS_CAST")
        project.unimined.events.register (register@{
            if (disable) return@register
            afterEvaluate()
        } as () -> Unit)
    }

    open fun afterEvaluate() {
        if (project.minecraft.combinedSourceSets.isNotEmpty()) {
            config("combined${baseTaskName.capitalize()}") {
                applyEnvConfig(EnvType.COMBINED, this)
                buildTask.dependsOn(this)
            }
        }
        if (project.minecraft.clientSourceSets.isNotEmpty()) {
            config("client${baseTaskName.capitalize()}") {
                applyEnvConfig(EnvType.CLIENT, this)
                buildTask.dependsOn(this)
            }
        }
        if (project.minecraft.serverSourceSets.isNotEmpty()) {
            config("server${baseTaskName.capitalize()}") {
                applyEnvConfig(EnvType.SERVER, this)
                buildTask.dependsOn(this)
            }
        }
    }

    abstract fun applyEnvConfig(env: EnvType, task: T)

}