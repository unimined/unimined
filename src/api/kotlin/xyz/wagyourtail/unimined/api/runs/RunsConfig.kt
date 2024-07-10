package xyz.wagyourtail.unimined.api.runs

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.runs.auth.AuthConfig
import xyz.wagyourtail.unimined.util.FinalizeOnRead

abstract class RunsConfig {

    /**
     * flag to disable all
     */
    var off: Boolean by FinalizeOnRead(false)
    abstract val auth: AuthConfig

    abstract fun config(
        config: String,
        action: RunConfig.() -> Unit
    )

    fun config(
        config: String,
        @DelegatesTo(
            value = RunConfig::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        config(config) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Internal
    abstract fun configFirst(
        config: String,
        action: RunConfig.() -> Unit
    )

    @ApiStatus.Internal
    fun configFirst(
        config: String,
        @DelegatesTo(
            value = RunConfig::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        configFirst(config) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * @since 1.2.5
     */
    abstract fun auth(action: AuthConfig.() -> Unit)

    fun auth(
        @DelegatesTo(
            value = AuthConfig::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        auth {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * @since 1.2.7
     */
    abstract fun all(action: RunConfig.() -> Unit)

    fun all(
        @DelegatesTo(
            value = RunConfig::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        all {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * add a task before RunClientV2 runs, this should not change the run config,
     * but is for stuff like copying the natives into the run dir.
     * @since 1.3.0
     */
    abstract fun preLaunch(config: String, action: RunConfig.() -> Unit)

    fun preLaunch(
        config: String,
        @DelegatesTo(
            value = RunConfig::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        preLaunch(config) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * @since 1.3.1
     */
    abstract fun preLaunch(config: String, action: TaskProvider<Task>)

    /**
     * @since 1.3.1
     */
    abstract fun preLaunch(config: String, action: Task)

    /**
     * @since 1.3.1
     */
    abstract fun preLaunch(config: String, action: String)

}
