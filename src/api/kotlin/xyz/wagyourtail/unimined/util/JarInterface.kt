package xyz.wagyourtail.unimined.util

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Task
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.bundling.Jar

interface JarInterface<T : Jar> : Task {

    @Suppress("UNCHECKED_CAST")
    val asJar: T
        @Internal
        get() = this as T

    fun asJar(action: T.() -> Unit) {
        asJar.action()
    }

    fun asJar(
        @DelegatesTo(Jar::class, strategy = Closure.DELEGATE_FIRST)
        closure: Closure<*>
    ) {
        asJar {
            closure.delegate = this
            closure.call()
        }
    }
}