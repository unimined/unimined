package xyz.wagyourtail.unimined.api.output.remap

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import xyz.wagyourtail.unimined.api.output.Output
import xyz.wagyourtail.unimined.api.tasks.RemapJarTask

/**
 * @since 0.5.0
 */
abstract interface RemapJarOutput : Output<RemapJarTask> {

    fun config(named: String,
        @DelegatesTo(
            value = RemapJarTask::class,
            strategy = Closure.DELEGATE_FIRST
        )
        action: Closure<*>
    ) {
        config(named) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

}