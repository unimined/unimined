package xyz.wagyourtail.unimined.api.launch

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.jetbrains.annotations.ApiStatus

class LaunchTransformer {

    /**
     * just a flag to disable all.
     */
    var off: Boolean = false

    private val transformers = mutableMapOf<String, LaunchConfig.() -> Unit>()

    fun setConfig(
        transformer: String,
        @DelegatesTo(
            value = LaunchConfig::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        setConfig(transformer) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun setConfig(
        transformer: String,
        action: LaunchConfig.() -> Unit
    ) {
        transformers.compute(transformer) { _, prev ->
            {
                prev?.invoke(this)
                action.invoke(this)
            }
        }
    }

    @Deprecated("use setConfig instead", ReplaceWith("setConfig(\"client\", action)"))
    fun setClient(@DelegatesTo(
        value = LaunchConfig::class,
        strategy = Closure.DELEGATE_FIRST
    ) action: Closure<*>) {
        setConfig("client", action)
    }

    @Deprecated("use setConfig instead", ReplaceWith("setConfig(\"client\", action)"))
    fun setClient(action: LaunchConfig.() -> Unit) {
        setConfig("client", action)
    }

    @Deprecated("use setConfig instead", ReplaceWith("setConfig(\"server\", action)"))
    fun setServer(@DelegatesTo(
        value = LaunchConfig::class,
        strategy = Closure.DELEGATE_FIRST
    ) action: Closure<*>) {
        setConfig("server", action)
    }

    @Deprecated("use setConfig instead", ReplaceWith("setConfig(\"server\", action)"))
    fun setServer(action: LaunchConfig.() -> Unit) {
        setConfig("server", action)
    }

    @ApiStatus.Internal
    fun transform(target: String, config: LaunchConfig) {
        transformers[target]?.invoke(config)
    }

    @ApiStatus.Internal
    fun getRegisteredTargets(): Set<String> = transformers.keys.toSet()
}