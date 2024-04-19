package xyz.wagyourtail.unimined.api.minecraft.patch.forge

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Dependency
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.patch.ataw.AccessTransformerPatcher
import xyz.wagyourtail.unimined.api.minecraft.patch.jarmod.JarModPatcher

/**
 * The class responsible for patching minecraft for forge.
 * @since 0.2.3
 */
interface ForgeLikePatcher<T: JarModPatcher> : JarModPatcher, AccessTransformerPatcher {

    @get:ApiStatus.Internal
    var forgeTransformer: T

    /**
     * add mixin configs for launch
     */
    var mixinConfig: List<String>

    @get:ApiStatus.Internal
    val remapAtToLegacy: Boolean

    /**
     * custom searge, also disables auto mcp in pre 1.7 as they are in the same file
     */
    @set:ApiStatus.Experimental
    var customSearge: Boolean

    /**
     * add mixin configs for launch
     */
    @Deprecated(message = "", replaceWith = ReplaceWith("mixinConfig"))
    var mixinConfigs: List<String>
        get() = mixinConfig
        set(value) {
            mixinConfig = value
        }

    fun mixinConfig(config: String) {
        mixinConfig = listOf(config)
    }

    fun mixinConfig(configs: List<String>) {
        mixinConfig = configs
    }

    fun mixinConfig(vararg configs: String) {
        mixinConfig = configs.toList()
    }

    fun loader(dep: Any) {
        loader(dep) {}
    }

    /**
     * set the version of forge to use
     * must be called
     * @since 1.0.0
     */
    fun loader(dep: Any, action: Dependency.() -> Unit)

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