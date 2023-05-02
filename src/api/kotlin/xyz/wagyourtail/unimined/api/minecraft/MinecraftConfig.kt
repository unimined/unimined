package xyz.wagyourtail.unimined.api.minecraft

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mappings.MappingsConfig

abstract class MinecraftConfig(val project: Project) {

    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    var side = EnvType.COMBINED

    fun side(sideConf: String) {
        side = EnvType.valueOf(sideConf.uppercase())
    }

    abstract val mappings: MappingsConfig

    fun mappings(action: MappingsConfig.() -> Unit) {
        mappings.action()
    }

    fun mappings(
        @DelegatesTo(value = MappingsConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mappings {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }



}