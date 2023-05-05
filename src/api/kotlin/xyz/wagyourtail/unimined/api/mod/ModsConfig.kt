package xyz.wagyourtail.unimined.api.mod

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig

abstract class ModsConfig(val project: Project, val minecraft: MinecraftConfig) {

    abstract fun remap(
        dependency: Any
    )

    abstract fun remap(
        dependency: Any,
        action: ModDepConfig.() -> Unit
    )

    fun remap(
        dependency: Any,
        @DelegatesTo(value = ModDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        remap(dependency) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

}