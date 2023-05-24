package xyz.wagyourtail.unimined.api.minecraft.patch

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Dependency
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import java.io.File
import java.nio.file.Path

/**
 * The class responsible for patching minecraft for fabric.
 *
 * usage:
 * ```groovy
 * fabric {
 *     loader "0.14.20"
 *     accessWidener "src/main/resources/myAccessWidener.cfg"
 *
 *     /* remaining are optional and auto set */
 *     devNamespace "yarn"
 *     devFallbackNamespace "intermediary"
 *
 *     /* use these if intermediary mappings aren't a thing for your version */
 *     prodNamespace "official"
 *     devMappings = null
 * }
 * ```
 * @since 0.2.3
 */
interface FabricLikePatcher: MinecraftPatcher, AccessTransformablePatcher {

    /**
     * 0.4.10 - make var for beta's and other official mapped versions
     * @since 0.2.3
     */
    @get:ApiStatus.Internal
    override var prodNamespace: MappingNamespace

    /**
     * @since 1.0.0
     */
    @get:ApiStatus.Internal
    @set:ApiStatus.Experimental
    var devMappings: Path?

    @set:ApiStatus.Experimental
    var customIntermediaries: Boolean

    /**
     * @since 1.0.0
     */
    fun loader(dep: Any) {
        loader(dep) {}
    }

    /**
     * set the version of fabric loader to use
     * must be called
     * @since 1.0.0
     */
    fun loader(dep: Any, action: Dependency.() -> Unit)

    /**
     * @since 1.0.0
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

    /**
     * @since 0.4.10
     */
    @Deprecated(message = "", replaceWith = ReplaceWith("prodNamespace(namespace)"))
    fun setProdNamespace(namespace: String) {
        prodNamespace = MappingNamespace.getNamespace(namespace)
    }

    /**
     * @since 1.0.0
     */
    fun prodNamespace(namespace: String) {
        prodNamespace = MappingNamespace.getNamespace(namespace)
    }

    /**
     * location of access widener file to apply to the minecraft jar.
     */
    var accessWidener: File?

    /**
     * set the access widener file to apply to the minecraft jar.
     */
    @Deprecated(message = "", replaceWith = ReplaceWith("accessWidener(file)"))
    fun setAccessWidener(file: String) {
        accessWidener = File(file)
    }

    fun accessWidener(file: String) {
        accessWidener = File(file)
    }

    fun accessWidener(file: Path) {
        accessWidener = file.toFile()
    }

    fun accessWidener(file: File) {
        accessWidener = file
    }

    fun mergeAws(inputs: List<File>): File
    fun mergeAws(namespace: MappingNamespace, inputs: List<File>): File
    fun mergeAws(output: File, inputs: List<File>): File
    fun mergeAws(output: File, namespace: MappingNamespace, inputs: List<File>): File
}