package xyz.wagyourtail.unimined.api.mapping

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Dependency

/**
 * @since 1.0.0
 */
abstract class MappingDepConfig<T : Dependency>(val dep: T, val mappingsConfig: MappingsConfig) : Dependency {

    /**
     * Maps namespace names for file formats that support naming namespaces.
     * If you use the name of a detected namespace for a file format that doesn't, it will still
     * work...
     *
     * This can be used for things such as changing which namespace is used for official
     * on pre-1.2.5, For example, with retroMCP they use client/server for the official mappings
     * and so you want to get those recognized as official mappings instead of the default
     * for unimined to use.
     */
    abstract fun mapNamespace(from: String, to: String)

    /**
     * set the source namespace for the current dependency.
     * this can be used to set the source on mappings where the first namespace is not
     * the source namespace. (ie. mojmap, where the reverse is true, but in that case this value defaults to official anyway)
     */
    abstract fun sourceNamespace(namespace: String)


    abstract fun sourceNamespace(mappingTypeToSrc: (String) -> String)

    fun sourceNamespace(
        mappingTypeToSrc: Closure<*>
    ) {
        sourceNamespace { mappingTypeToSrc.call(it) as String }
    }

    /**
     * filters namespaces to have to be from this.
     * applied after mapNamespace
     */
    abstract fun outputs(namespace: String, named: Boolean, canRemapTo: () -> List<String>): MappingNamespaceTree.Namespace

    fun outputs(
        namespace: String,
        named: Boolean,
        @DelegatesTo(
            value = MappingNamespaceTree.Namespace::class,
            strategy = Closure.DELEGATE_FIRST
        )
        canRemapTo: Closure<*>
    ) {
        outputs(namespace, named) {
            canRemapTo.delegate = this
            canRemapTo.resolveStrategy = Closure.DELEGATE_FIRST
            @Suppress("UNCHECKED_CAST")
            canRemapTo.call() as List<String>
        }
    }
}