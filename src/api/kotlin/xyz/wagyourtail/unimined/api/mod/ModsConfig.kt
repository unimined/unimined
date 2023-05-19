package xyz.wagyourtail.unimined.api.mod

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Configuration
import org.jetbrains.annotations.ApiStatus
import java.io.File

abstract class ModsConfig {

    fun remap(config: Configuration) {
        remap(listOf(config)) {}
    }

    fun remap(vararg config: Configuration) {
        remap(config.toList()) {}
    }

    fun remap(config: List<Configuration>) {
        remap(config) {}
    }

    fun remap(
        config: Configuration,
        action: ModRemapSettings.() -> Unit
    ) {
        remap(listOf(config), action)
    }

    fun remap(
        vararg config: Configuration,
        action: ModRemapSettings.() -> Unit
    ) {
        remap(config.toList(), action)
    }

    abstract fun remap(config: List<Configuration>, action: ModRemapSettings.() -> Unit)


    fun remap(
        config: Configuration,
        @DelegatesTo(value = ModRemapSettings::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        remap(config) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun remap(
        config: List<Configuration>,
        @DelegatesTo(value = ModRemapSettings::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        remap(config) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }
}