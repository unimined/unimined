package xyz.wagyourtail.unimined.api.output

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus

/**
 * @since 0.5.0
 */
abstract interface Output<T: Jar> {

    var disable: Boolean

    fun config(named: String, apply: T.() -> Unit)

    @ApiStatus.Internal
    fun configFirst(named: String, apply: T.() -> Unit)

    fun configAll(apply: T.(name: String) -> Unit)

    @ApiStatus.Internal
    fun configAllFirst(apply: T.(name: String) -> Unit)

    fun config(named: String,
        @DelegatesTo(
            value = Jar::class,
            strategy = Closure.DELEGATE_FIRST
        )
        apply: Closure<*>
    ) {
        config(named) {
            apply.delegate = this
            apply.resolveStrategy = Closure.DELEGATE_FIRST
            apply.call()
        }
    }

    //TODO: test these
    fun configAll(
        @DelegatesTo(
            value = Jar::class,
            strategy = Closure.DELEGATE_FIRST
        )
        apply: Closure<*>
    ) {
        configAll {
            apply.delegate = this
            apply.resolveStrategy = Closure.DELEGATE_FIRST
            apply.call(it)
        }
    }
}
