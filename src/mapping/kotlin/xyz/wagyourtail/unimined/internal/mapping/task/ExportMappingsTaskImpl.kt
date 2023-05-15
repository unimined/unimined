package xyz.wagyourtail.unimined.internal.mapping.task

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingWriter
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.MCPWriter
import net.fabricmc.mappingio.format.MappingDstNsFilter
import net.fabricmc.mappingio.format.SrgWriter
import net.fabricmc.mappingio.format.Tiny2Writer2
import net.fabricmc.mappingio.tree.MappingTreeView
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.org.eclipse.jgit.lib.ObjectChecker
import xyz.wagyourtail.unimined.api.task.ExportMappingsTask
import xyz.wagyourtail.unimined.internal.mapping.MappingsProvider
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

class ExportMappingsTaskImpl(val mappings: MappingsProvider) : ExportMappingsTask() {

    private val exports = mutableSetOf<ExportImpl>()

    @TaskAction
    fun run() {
        project.logger.lifecycle("[Unimined/ExportMappings $path] Exporting mappings...")
        for (export in exports) {
            project.logger.info("[Unimined/ExportMappings $path] Exporting mappings to ${export.location}")
            project.logger.debug(
                "[Unimined/ExportMappings $path] ${export.type} ${export.sourceNamespace} -> [${
                    export.targetNamespace?.joinToString(
                        ", "
                    )
                }}]"
            )
            export.exportFunc(mappings.mappingTree)
        }
    }

    override fun export(action: Export.() -> Unit) {
        val export = ExportImpl()
        export.action()
        export.validate()
        exports.add(export)
    }

    class ExportImpl : Export() {

        override var exportFunc: (MappingTreeView) -> Unit = ::export

        private fun export(mappingTree: MappingTreeView) {
            location!!.parentFile.mkdirs()
            location!!.toPath()
                .outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .use { os ->
                    var visitor = when (type) {
                        MappingExportTypes.MCP -> MCPWriter(os, envType!!.ordinal)
                        MappingExportTypes.TINY_V2 -> {
                            val stream = if (location!!.extension == "jar" || location!!.extension == "zip") {
                                ZipOutputStream(os).apply {
                                    putNextEntry(ZipEntry("mappings/mappings.tiny"))
                                }
                            } else os
                            Tiny2Writer2(
                                OutputStreamWriter(stream, StandardCharsets.UTF_8),
                                false
                            )
                        }

                        MappingExportTypes.SRG -> SrgWriter(OutputStreamWriter(os, StandardCharsets.UTF_8))
                        else -> throw RuntimeException("Unknown export type ${ObjectChecker.type}")
                    }
                    if (skipComments) {
                        visitor = object: MappingWriter by visitor {
                            override fun visitComment(targetKind: MappedElementKind?, comment: String?) {}
                        }
                    }
                    mappingTree.accept(
                        MappingSourceNsSwitch(
                            MappingDstNsFilter(
                                MappingNsRenamer(
                                    visitor,
                                    renameNs.mapKeys { it.key.namespace }
                                ),
                                targetNamespace?.map { it.namespace } ?: mappingTree.dstNamespaces
                            ),
                            sourceNamespace?.namespace ?: mappingTree.srcNamespace
                        ),
                    )
                    os.flush()
                    visitor.close()
                }
        }
    }
}