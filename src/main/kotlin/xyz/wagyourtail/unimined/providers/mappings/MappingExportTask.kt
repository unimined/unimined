package xyz.wagyourtail.unimined.providers.mappings

import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.MCPWriter
import net.fabricmc.mappingio.format.MappingDstNsFilter
import net.fabricmc.mappingio.format.SrgWriter
import net.fabricmc.mappingio.format.Tiny2Writer2
import net.fabricmc.mappingio.tree.MappingTreeView
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.TaskAction
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.UniminedExtension
import xyz.wagyourtail.unimined.providers.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.outputStream

open class MappingExportTask : ConventionTask() {
    private val minecraftProvider = project.extensions.getByType(MinecraftProvider::class.java)
    private val uniminedExtension = project.extensions.getByType(UniminedExtension::class.java)

    private val exports: MutableMap<EnvType, MutableSet<MappingExport>> = mutableMapOf()

    @TaskAction
    fun run() {
        project.logger.lifecycle("Exporting mappings...")
        for (envType in EnvType.values()) {
            if (envType == EnvType.COMBINED && minecraftProvider.disableCombined.get()) continue
            val mappings = uniminedExtension.mappingsProvider.getMappingTree(envType)
            val exportSet = exports[envType] ?: continue
            for (export in exportSet) {
                project.logger.info("Exporting mappings to ${export.location}")
                project.logger.debug("${export.type} ${export.sourceNamespace} -> [${export.targetNamespace?.joinToString(", ") }}]")
                export.exportFunc(mappings)
            }
        }
    }

    fun export(envType: String, export: (MappingExport) -> Unit) {
        addExport(EnvType.valueOf(envType), export)
    }

    @ApiStatus.Internal
    fun addExport(envType: EnvType, export: (MappingExport) -> Unit) {
        val me = MappingExport(envType)
        export(me)
        me.validate()
        exports.computeIfAbsent(envType) { mutableSetOf() }.add(me)
    }
}

class MappingExport(val envType: EnvType) {
    @set:ApiStatus.Internal
    var type: MappingExportTypes? = null
    var location: File? = null
    var sourceNamespace: String? = null
    var targetNamespace: List<String>? = null
    @set:ApiStatus.Internal
    var exportFunc: (MappingTreeView) -> Unit = ::export

    fun setType(type: String) {
        this.type = MappingExportTypes.valueOf(type.uppercase(Locale.getDefault()))
    }

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

    private fun export(mappingTree: MappingTreeView) {
        location!!.toPath()
            .outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            .use { os ->
                val visitor = when (type) {
                    MappingExportTypes.MCP -> MCPWriter(os, envType.ordinal)
                    MappingExportTypes.TINY_V2 -> Tiny2Writer2(
                        OutputStreamWriter(os, StandardCharsets.UTF_8),
                        false
                    )

                    MappingExportTypes.SRG -> SrgWriter(OutputStreamWriter(os, StandardCharsets.UTF_8))
                    else -> throw RuntimeException("Unknown export type $type")
                }
                mappingTree.accept(
                    MappingSourceNsSwitch(
                        MappingDstNsFilter(
                            visitor, targetNamespace ?: mappingTree.dstNamespaces
                        ), sourceNamespace ?: mappingTree.srcNamespace
                    )
                )
                os.flush()
                visitor.close()
            }
    }
}

enum class MappingExportTypes {
    TINY_V2, SRG, MCP
}