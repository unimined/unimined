package xyz.wagyourtail.unimined.internal.mapping.task

import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.org.eclipse.jgit.lib.ObjectChecker
import xyz.wagyourtail.unimined.api.mapping.task.ExportMappingsTask
import xyz.wagyourtail.unimined.internal.mapping.MappingsProvider
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.JavadocParentNode
import xyz.wagyourtail.unimined.mapping.visitor.JavadocVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.Delegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.io.path.outputStream


open class ExportMappingsTaskImpl @Inject constructor(@get:Internal val mappings: MappingsProvider) : ExportMappingsTask() {

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
                }]"
            )
            runBlocking {
                export.exportFunc(mappings.resolve())
            }
        }
    }

    override fun export(action: Export.() -> Unit) {
        val export = ExportImpl(mappings)
        export.action()
        export.validate()
        exports.add(export)
    }

    class ExportImpl(val mappings: MappingsProvider) : Export() {

        override var exportFunc: (AbstractMappingTree) -> Unit = ::export
        override fun setSourceNamespace(namespace: String) {
            sourceNamespace = Namespace(namespace)
        }

        override fun setTargetNamespaces(namespace: List<String>) {
            targetNamespace = namespace.map { Namespace(it) }.toSet()
        }

        private fun export(mappingTree: AbstractMappingTree) {
            location!!.parentFile.mkdirs()
            location!!.toPath()
                .outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .use { os ->
                    os.sink().buffer().use { sink ->
                        var write = type!!.write(sink, envType ?: EnvType.JOINED)
                        if (skipComments) {
                            write = write.delegator(object : Delegator() {
                                override fun visitJavadoc(
                                    delegate: JavadocParentNode<*>,
                                    value: String,
                                    namespaces: Set<Namespace>
                                ): JavadocVisitor? {
                                    return null
                                }
                            })
                        }
                        mappingTree.accept(
                            write,
                            listOf(sourceNamespace!!, *targetNamespace!!.toTypedArray())
                        )
                    }
                }
        }
    }
}