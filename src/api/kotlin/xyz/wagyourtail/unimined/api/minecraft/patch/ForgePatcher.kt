package xyz.wagyourtail.unimined.api.minecraft.patch

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Dependency
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.patch.JarModPatcher
import java.io.File
import java.nio.file.Path

/**
 * The class responsible for patching minecraft for forge.
 * @since 0.2.3
 */
interface ForgePatcher: JarModPatcher, AccessTransformablePatcher {

    /**
     * location of access transformer file to apply to the minecraft jar.
     */
    var accessTransformer: File?

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

    fun forge(dep: Any) {
        forge(dep) {}
    }

    /**
     * set the version of forge to use
     * must be called
     * @since 1.0.0
     */
    fun forge(dep: Any, action: Dependency.() -> Unit)

    fun forge(
        dep: Any,
        @DelegatesTo(
            value = Dependency::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        forge(dep) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }


    /**
     * set the access transformer file to apply to the minecraft jar.
     */
    @Deprecated(message = "", replaceWith = ReplaceWith("accessTransformer(file)"))
    fun setAccessTransformer(file: String) {
        accessTransformer = File(file)
    }

    /**
     * set the access transformer file to apply to the minecraft jar.
     * @since 1.0.0
     */
    fun accessTransformer(file: String) {
        accessTransformer = File(file)
    }

    /**
     * set the access transformer file to apply to the minecraft jar.
     * @since 1.0.0
     */
    fun accessTransformer(file: Path) {
        accessTransformer = file.toFile()
    }

    /**
     * set the access transformer file to apply to the minecraft jar.
     * @since 1.0.0
     */
    fun accessTransformer(file: File) {
        accessTransformer = file
    }

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: String): File

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: String, output: String): File

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: File): File

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: File, output: File): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: String): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: String, output: String): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: File): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: File, output: File): File
}