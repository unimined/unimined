package xyz.wagyourtail.unimined.api.output

import org.gradle.api.Task

/**
 * @since 0.5.0
 */
abstract interface Output<T: Task> {

    var disable: Boolean

    fun config(named: String, apply: T.() -> Unit)

}
