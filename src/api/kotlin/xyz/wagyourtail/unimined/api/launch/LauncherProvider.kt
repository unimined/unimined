package xyz.wagyourtail.unimined.api.launch

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.jetbrains.annotations.ApiStatus


/**
 * @since 0.3.0
 * @revised 0.4.0
 */
abstract class LauncherProvider {

    /**
     * just a flag to disable all.
     */
    var off: Boolean = false

    protected val transformers = mutableMapOf<String, LaunchConfig.() -> Unit>()

    fun config(
        config: String,
        @DelegatesTo(
            value = LaunchConfig::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        config(config) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    abstract fun config(
        config: String,
        action: LaunchConfig.() -> Unit
    )

    @Deprecated("use setConfig instead", ReplaceWith("setConfig(\"client\", action)"))
    fun setClient(@DelegatesTo(
        value = LaunchConfig::class,
        strategy = Closure.DELEGATE_FIRST
    ) action: Closure<*>) {
        config("client", action)
    }

    @Deprecated("use setConfig instead", ReplaceWith("setConfig(\"client\", action)"))
    fun setClient(action: LaunchConfig.() -> Unit) {
        config("client", action)
    }

    @Deprecated("use setConfig instead", ReplaceWith("setConfig(\"server\", action)"))
    fun setServer(@DelegatesTo(
        value = LaunchConfig::class,
        strategy = Closure.DELEGATE_FIRST
    ) action: Closure<*>) {
        config("server", action)
    }

    @Deprecated("use setConfig instead", ReplaceWith("setConfig(\"server\", action)"))
    fun setServer(action: LaunchConfig.() -> Unit) {
        config("server", action)
    }

    @get:ApiStatus.Internal
    val registeredTargets: Set<String>
        get() = transformers.keys.toSet()

    @ApiStatus.Internal
    abstract fun addTarget(config: LaunchConfig)
}