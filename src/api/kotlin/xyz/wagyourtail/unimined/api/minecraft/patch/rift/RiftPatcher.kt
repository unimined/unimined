package xyz.wagyourtail.unimined.api.minecraft.patch.rift

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.patch.MinecraftPatcher
import xyz.wagyourtail.unimined.api.minecraft.patch.ataw.AccessTransformerPatcher

/**
 * The class responsible for patching minecraft for rift.
 * @since 1.2.0
 */
interface RiftPatcher : MinecraftPatcher, AccessTransformerPatcher {

    /**
     * @since 1.2.0
     */
    fun loader(dep: Any) {
        loader(dep) {}
    }

    /**
     * set the version of rift to use
     * must be called
     * @since 1.2.0
     */
    fun loader(dep: Any, action: Dependency.() -> Unit)

    /**
     * @since 1.2.0
     */
    fun loader(
        dep: Any,
        @DelegatesTo(
            value = Dependency::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        loader(dep) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }
}