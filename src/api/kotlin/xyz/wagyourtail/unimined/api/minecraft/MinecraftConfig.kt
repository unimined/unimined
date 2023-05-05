package xyz.wagyourtail.unimined.api.minecraft

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.MinecraftPatcher
import xyz.wagyourtail.unimined.api.mod.ModsConfig
import xyz.wagyourtail.unimined.api.runs.RunsConfig
import xyz.wagyourtail.unimined.api.task.RemapJarTask

/**
 * @since 1.0.0
 */
abstract class MinecraftConfig(val project: Project, val sourceSet: SourceSet) : PatchProviders {

    @set:ApiStatus.Internal
    var side = EnvType.COMBINED

    fun side(sideConf: String) {
        side = EnvType.valueOf(sideConf.uppercase())
    }

    @set:ApiStatus.Internal
    abstract var mcPatcher: MinecraftPatcher

    abstract val mappings: MappingsConfig
    abstract val mods: ModsConfig
    abstract val runs: RunsConfig

    fun mappings(action: MappingsConfig.() -> Unit) {
        mappings.action()
    }

    fun mappings(
        @DelegatesTo(value = MappingsConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mappings {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun remap(task: Task) {
        remap(task) {}
    }

    abstract fun remap(task: Task, action: RemapJarTask.() -> Unit)

    fun remap(
        task: Task,
        @DelegatesTo(value = RemapJarTask::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        remap(task) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun remap(task: Task, name: String) {
        remap(task, name) {}
    }

    abstract fun remap(task: Task, name: String, action: RemapJarTask.() -> Unit)

    fun remap(
        task: Task,
        name: String,
        @DelegatesTo(value = RemapJarTask::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        remap(task, name) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun mods(action: ModsConfig.() -> Unit) {
        mods.action()
    }

    fun mods(
        @DelegatesTo(value = ModsConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mods {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun runs(action: RunsConfig.() -> Unit) {
        runs.action()
    }

    fun runs(
        @DelegatesTo(value = RunsConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        runs {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

}