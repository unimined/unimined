package xyz.wagyourtail.unimined.api.run

import groovy.lang.Closure
import groovy.lang.DelegatesTo

class Runs {

    var client: RunConfig.() -> Unit = {}

    fun setClient(@DelegatesTo(
        value = RunConfig::class,
        strategy = Closure.DELEGATE_FIRST
    ) action: Closure<*>) {
        val prev = client
        client = {
            prev()
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    var server: RunConfig.() -> Unit = {}

    fun setServer(@DelegatesTo(
        value = RunConfig::class,
        strategy = Closure.DELEGATE_FIRST
    ) action: Closure<*>) {
        val prev = server
        server = {
            prev()
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }
}