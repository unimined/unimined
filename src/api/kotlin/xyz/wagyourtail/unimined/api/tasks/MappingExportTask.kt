package xyz.wagyourtail.unimined.api.tasks

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.mappingio.tree.MappingTreeView
import org.gradle.api.internal.ConventionTask
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import java.io.File

/**
 * Task to export mappings to files.
 * @since 0.2.3
 *
 * usage:
 * ```
 * exportMappings {
 *      export("COMBINED") {
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
abstract class MappingExportTask : ConventionTask() {

    /**
     * export a mapping to a file.
     * @since 0.2.3
     */
    abstract fun export(envType: String, export: (MappingExport) -> Unit)

    /**
     * export a mapping to a file.
     * @since 0.2.3
     */
    fun export(
        envType: String, @DelegatesTo(
            value = MappingExport::class, strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        export(envType) {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Internal
    abstract fun addExport(envType: EnvType, export: (MappingExport) -> Unit)
}

/**
 * definition for a mapping export
 */
abstract class MappingExport(val envType: EnvType) {
    @set:ApiStatus.Internal
    var type: MappingExportTypes? = null

    /**
     * the file location to export to.
     */
    var location: File? = null

    /**
     * the namespace to export from.
     * @since 0.2.3
     */
    var sourceNamespace: String? = null

    /**
     * the namespace(s) to export to.
     * @since 0.2.3
     */
    var targetNamespace: List<String>? = null

    @set:ApiStatus.Experimental
    abstract var exportFunc: (MappingTreeView) -> Unit

    /**
     * the format to export to. (SRG, TINY_V2, MCP)
     */
    fun setType(type: String) {
        this.type = MappingExportTypes.valueOf(type)
    }

    /**
     * the location to export to.
     */
    fun setLocation(location: String) {
        this.location = File(location)
    }

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
        if (type == MappingExportTypes.MCP || type == MappingExportTypes.SRG) {
            if (targetNamespace!!.size != 1) {
                throw IllegalArgumentException("Mapping export target namespace must be a single namespace for ${type!!.name} exports.")
            }
        }
        return true
    }
}

enum class MappingExportTypes {
    TINY_V2, SRG, MCP
}