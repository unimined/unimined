package xyz.wagyourtail.unimined.api.output.remap

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import xyz.wagyourtail.unimined.api.output.Output
import xyz.wagyourtail.unimined.api.task.RemapJarTask

/**
 * @since 0.5.0
 */
abstract interface RemapJarOutput : Output<RemapJarTask> {

    override fun config(named: String,
        @DelegatesTo(
            value = RemapJarTask::class,
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
    override fun configAll(
        @DelegatesTo(
            value = RemapJarTask::class,
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