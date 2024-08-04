package xyz.wagyourtail.unimined.api.mapping.task

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.internal.ConventionTask
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import java.io.File

/**
 * Task to export mappings to files.
 * @since 0.2.3
 * @revised 1.0.0
 * usage:
 * ```
 * exportMappings {
 *      export {
 *          sourceNamespace = "intermediary"
 *          targetNamespace = ["named"]
 *          output = file("$projectDir/src/main/resources/mappings.srg")
 *          format = "SRG"
 *      }
 * }
 *
 * processResources.dependsOn(exportMappings)
 * ```
 */
abstract class ExportMappingsTask : ConventionTask() {

    /**
     * export a mapping to a file.
     * @since 0.2.3
     * @revised 1.0.0
     */
    abstract fun export(action: Export.() -> Unit)

    /**
     * export a mapping to a file.
     * @since 0.2.3
     * @revised 1.0.0
     */
    fun export(
         @DelegatesTo(
            value = Export::class, strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        export {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }


    /**
     * definition for a mapping export
     */
    abstract class Export {

        /**
         * only used in mcp writer now
         * @since 0.2.3
         * @revised 1.0.0
         */
        @set:ApiStatus.Internal
        var envType: EnvType? = null

        fun envType(env: String) {
            envType = EnvType.valueOf(env.uppercase())
        }

        @set:ApiStatus.Internal
        var type: FormatWriter? = null

        /**
         * the file location to export to.
         */
        var location: File? = null

        /**
         * the namespace to export from.
         * @since 0.2.3
         */
        @set:ApiStatus.Internal
        var sourceNamespace: Namespace? = null

        /**
         * the namespace(s) to export to.
         * @since 0.2.3
         */
        @set:ApiStatus.Internal
        var targetNamespace: Set<Namespace>? = null

        /**
         * should the export skip comments?
         * @since 0.3.6
         */
        var skipComments: Boolean = false

        /**
         * function for exporting the mapping tree
         * @since 0.3.4
         */
        @set:ApiStatus.Experimental
        abstract var exportFunc: (AbstractMappingTree) -> Unit

        /**
         * rename a namespace in the exported format, if supported.
         * @since 0.3.9
         */
        val renameNs: MutableMap<Namespace, String> = mutableMapOf()

        /**
         * the format to export to. (SRG, TINY_V2, MCP)
         */
        fun setType(type: String) {
            this.type = FormatRegistry.byName[type.lowercase()]
        }

        /**
         * the location to export to.
         */
        fun setLocation(location: String) {
            this.location = File(location)
        }

        abstract fun setSourceNamespace(namespace: String)

        abstract fun setTargetNamespaces(namespace: List<String>)

        @ApiStatus.Internal
        fun validate(): Boolean {
            if (type == null) {
                throw IllegalArgumentException("Mapping export type must be set.")
            }
            if (location == null) {
                throw IllegalArgumentException("Mapping export location must be set.")
            }
            if (sourceNamespace == null) {
                throw IllegalArgumentException("Mapping export source namespace must be set.")
            }
            if (targetNamespace == null || targetNamespace!!.isEmpty()) {
                throw IllegalArgumentException("Mapping export target namespace must be set.")
            }
            return true
        }
    }

}