package xyz.wagyourtail.unimined.api.mapping

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromAbstractTypeMethods
import groovy.transform.stc.FromString
import groovy.transform.stc.SimpleType
import groovy.transform.stc.SingleSignatureClosureHint
import org.gradle.api.artifacts.Dependency

/**
 * @since 1.0.0
 */
abstract class MappingDepConfig(val dep: Dependency, val mappingsConfig: MappingsConfig): ContainedMapping {

    abstract class TempMappingNamespace(val namespace: String, val named: Boolean, val canRemapTo: () -> List<String>) {
        abstract val actualNamespace: MappingNamespaceTree.Namespace
    }

    abstract fun contains(acceptor: (fname: String, type: String) -> Boolean, action: ContainedMapping.() -> Unit)

    fun contains(
        @ClosureParams(
            value = SimpleType::class,
            options = [
                "java.lang.String",
                "java.lang.String"
            ]
        )
        acceptor: Closure<*>,
        @DelegatesTo(
            value = ContainedMapping::class,
            strategy = Closure.DELEGATE_FIRST
        )
        action: Closure<*>
    ) {
        contains({ f, t ->
            acceptor.call(f, t) as Boolean
        }) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    abstract fun clearContains()
}


interface ContainedMapping {
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
    fun mapNamespace(from: String, to: String)

    /**
     * set the source namespace for the current dependency.
     * this can be used to set the source on mappings where the first namespace is not
     * the source namespace. (ie. mojmap, where the reverse is true, but in that case this value defaults to official anyway)
     */
    fun sourceNamespace(namespace: String)

    /**
     * insert forward visitor that converts srg to searge names.
     */
    fun srgToSearge()

    /**
     * insert forward visitor that only allows existing src names.
     */
    fun onlyExistingSrc()

    /**
     * insert forward visitor that strips child method mappings.
     */
    fun childMethodStrip()

    fun clearForwardVisitor()

    /**
     * filters namespaces to have to be from this.
     * applied after mapNamespace
     */
    fun outputs(namespace: String, named: Boolean, canRemapTo: () -> List<String>): MappingDepConfig.TempMappingNamespace

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

    fun clearOutputs()
}