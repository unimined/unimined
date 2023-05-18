package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import java.io.File

/**
 * The class responsible for patching minecraft for fabric.
 * @since 0.2.3
 */
interface FabricLikePatcher: MinecraftPatcher, AccessTransformablePatcher {

    /**
     * 0.4.10 - make var for beta's and other official mapped versions
     * @since 0.2.3
     */
    override var prodNamespace: MappingNamespace

    fun loader(dep: Any) {
        loader(dep) {}
    }

    /**
     * set the version of fabric loader to use
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



    /**
     * @since 0.4.10
     */
    fun setProdNamespace(namespace: String) {
        prodNamespace = MappingNamespace.getNamespace(namespace)
    }

    /**
     * location of access widener file to apply to the minecraft jar.
     */
    var accessWidener: File?

    /**
     * set the access widener file to apply to the minecraft jar.
     */
    fun setAccessWidener(file: String) {
        accessWidener = File(file)
    }

    fun mergeAws(inputs: List<File>): File
    fun mergeAws(namespace: MappingNamespace, inputs: List<File>): File
    fun mergeAws(output: File, inputs: List<File>): File
    fun mergeAws(output: File, namespace: MappingNamespace, inputs: List<File>): File
}