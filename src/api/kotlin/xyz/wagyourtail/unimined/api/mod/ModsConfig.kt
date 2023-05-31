package xyz.wagyourtail.unimined.api.mod

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Configuration

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
        action: ModRemapConfig.() -> Unit
    ) {
        remap(listOf(config), action)
    }

    fun remap(
        vararg config: Configuration,
        action: ModRemapConfig.() -> Unit
    ) {
        remap(config.toList(), action)
    }

    abstract fun remap(config: List<Configuration>, action: ModRemapConfig.() -> Unit)


    fun remap(
        config: Configuration,
        @DelegatesTo(value = ModRemapConfig::class, strategy = Closure.DELEGATE_FIRST)
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
        @DelegatesTo(value = ModRemapConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        remap(config) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    abstract fun modImplementation(action: ModRemapConfig.() -> Unit)

    fun modImplementation(
        @DelegatesTo(value = ModRemapConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        modImplementation {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }
}