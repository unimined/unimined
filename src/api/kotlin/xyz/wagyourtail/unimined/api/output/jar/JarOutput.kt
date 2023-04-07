package xyz.wagyourtail.unimined.api.output.jar

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.output.Output

abstract interface JarOutput: Output<Jar> {

    fun config(named: String,
        @DelegatesTo(
            value = Jar::class,
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
