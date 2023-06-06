package xyz.wagyourtail.unimined.api.mapping

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Dependency

/**
 * @since 1.0.0
 */
abstract class MappingDepConfig(val dep: Dependency, val mappingsConfig: MappingsConfig) {

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


    /**
     * set the source namespace for the current dependency.
     * this version can be used if a single dependency provides multiple files.
     * such as forge-src. in which case, the type of the mappings can be used to determine the src namespace.
     *
     * obviously this won't work for things like multiple .tiny files in one zip,
     * if anyone runs into a real-world example like that, contact me, I might add support via
     * using the available namespace list and list of already read namespaces instead.
     */
    abstract fun sourceNamespace(mappingTypeToSrc: (String) -> String)

    fun sourceNamespace(
        mappingTypeToSrc: Closure<*>
    ) {
        sourceNamespace { mappingTypeToSrc.call(it) as String }
    }

    /**
     * insert forward visitor that converts srg to searge names.
     */
    abstract fun srgToSearge()

    /**
     * insert forward visitor that only allows existing src names.
     */
    abstract fun onlyExistingSrc()

    /**
     * insert forward visitor that strips child method mappings.
     */
    abstract fun childMethodStrip()

    abstract fun clearForwardVisitor()

    /**
     * filters namespaces to have to be from this.
     * applied after mapNamespace
     */
    abstract fun outputs(namespace: String, named: Boolean, canRemapTo: () -> List<String>): TempMappingNamespace

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

    abstract fun clearOutputs()

    abstract class TempMappingNamespace(val namespace: String, val named: Boolean, val canRemapTo: () -> List<String>) {
        abstract val actualNamespace: MappingNamespaceTree.Namespace
    }
}