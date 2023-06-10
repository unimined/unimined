package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import java.io.Reader

@Suppress("UNUSED", "UNUSED_PARAMETER", "UNUSED_VARIABLE")
object OlderMCPReader {

    fun readMethod(
        envType: EnvType,
        reader: Reader,
        sourceNamespace: String,
        targetNamespace: String,
        mappingTree: MappingTreeView,
        visitor: MappingVisitor
    ) {
        readMethod(envType, ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, mappingTree, visitor)
    }

    private fun readMethod(
        envType: EnvType,
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        mappingTree: MappingTreeView,
        visitor: MappingVisitor
    ) {
        reader.nextLine(0)
        reader.nextLine(0)
        reader.nextLine(0)

        if (reader.readCell()?.startsWith("class") != true) {
            throw IllegalStateException("Expected older mcp header")
        }

        val methods = mutableMapOf<String, MethodData>()

        while (reader.nextLine(0)) {
            val clientClassName = reader.readCell()
            val clientSrg = reader.readCell()

            val serverClassName = reader.readCell()
            val serverSrg = reader.readCell()

            val methodName = reader.readCell()
            val comment = reader.readCell()

            if (clientSrg == null || serverSrg == null || methodName == null) {
                System.err.println("Invalid method on line ${reader.lineNumber}: $clientClassName $clientSrg $serverClassName $serverSrg $methodName $comment")
                continue
            }

            if (envType == EnvType.CLIENT && (clientSrg.isEmpty() || clientSrg == "*")) {
                continue
            } else {
                methods[clientSrg] = MethodData(clientSrg, methodName, comment)
            }

            if (envType == EnvType.SERVER && (serverSrg.isEmpty() || serverSrg == "*")) {
                continue
            } else {
                methods[serverSrg] = MethodData(serverSrg, methodName, comment)
            }
        }

        val parentVisitor = visitor
//        @Suppress("NAME_SHADOWING")
//        val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        val seargeNamespace = mappingTree.getNamespaceId(sourceNamespace)
        if (seargeNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $sourceNamespace not found")
        }

        var visitLastClass: Boolean


        if (visitor.visitContent()) {
            for (clazz in mappingTree.classes) {
                val cn = clazz.getName(seargeNamespace) ?: continue
                visitLastClass = visitor.visitClass(cn)

                if (visitLastClass) {
                    visitor.visitDstName(MappedElementKind.CLASS, 0, cn)
                }
            }

            for (clazz in mappingTree.classes) {
                visitLastClass = visitor.visitClass(clazz.getName(seargeNamespace) ?: continue)
                if (visitLastClass) {
                    visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)

                    if (visitLastClass) {
                        for (meth in clazz.methods) {
                            if (meth.getName(seargeNamespace) in methods) {
                                val method = methods[meth.getName(seargeNamespace)]!!
                                visitor.visitMethod(method.source, meth.getDesc(seargeNamespace))
                                visitor.visitDstName(MappedElementKind.METHOD, 0, method.target)
                                visitor.visitElementContent(MappedElementKind.METHOD)
                                if (!method.desc.isNullOrEmpty() && method.desc != "*") {
                                    visitor.visitComment(MappedElementKind.METHOD, method.desc)
                                }
                            }
                        }
                    }
                }
            }
        }

        visitor.visitEnd()

//        visitor.accept(parentVisitor)
    }

    internal fun readParam(
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        mappingTree: MappingTreeView,
        visitor: MappingVisitor
    ) {
        throw UnsupportedOperationException("Older MCPReader does not support reading param mappings")
    }

    fun readField(
        envType: EnvType,
        reader: Reader,
        sourceNamespace: String,
        targetNamespace: String,
        mappingTree: MappingTreeView,
        visitor: MappingVisitor
    ) {
        readField(envType, ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, mappingTree, visitor)
    }

    private fun readField(
        envType: EnvType,
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        mappingTree: MappingTreeView,
        visitor: MappingVisitor
    ) {
        reader.nextLine(0)
        reader.nextLine(0)

        if (reader.readCell()?.startsWith("Class") != true) {
            throw IllegalStateException("Expected older mcp header")
        }

        val methods = mutableMapOf<String, FieldData>()

        while (reader.nextLine(0)) {
            val clientClassName = reader.readCell()
            val idk = reader.readCell()
            val clientSrg = reader.readCell()

            val serverClassName = reader.readCell()
            val idk2 = reader.readCell()
            val serverSrg = reader.readCell()

            val fieldName = reader.readCell()
            val comment = reader.readCell()

            if (clientSrg == null || serverSrg == null || fieldName == null) {
                System.err.println("Invalid field on line ${reader.lineNumber}: $clientClassName $idk $clientSrg $serverClassName $idk2 $serverSrg $fieldName $comment")
                continue
            }

            if (envType == EnvType.CLIENT && (clientSrg.isEmpty() || clientSrg == "*")) {
                continue
            } else {
                methods[clientSrg] = FieldData(clientSrg, fieldName, comment)
            }

            if (envType == EnvType.SERVER && (serverSrg.isEmpty() || serverSrg == "*")) {
                continue
            } else {
                methods[serverSrg] = FieldData(serverSrg, fieldName, comment)
            }
        }

        val parentVisitor = visitor

        @Suppress("NAME_SHADOWING")
        val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        val seargeNamespace = mappingTree.getNamespaceId(sourceNamespace)
        if (seargeNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $sourceNamespace not found")
        }

        var visitLastClass: Boolean


        if (visitor.visitContent()) {
            for (clazz in mappingTree.classes) {
                val cn = clazz.getName(seargeNamespace) ?: continue
                visitLastClass = visitor.visitClass(cn)

                if (visitLastClass) {
                    visitor.visitDstName(MappedElementKind.CLASS, 0, cn)
                    visitor.visitElementContent(MappedElementKind.CLASS)
                }
            }

            for (clazz in mappingTree.classes) {
                visitLastClass = visitor.visitClass(clazz.getName(seargeNamespace) ?: continue)
                if (visitLastClass) {
                    visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)

                    if (visitLastClass) {
                        for (meth in clazz.fields) {
                            if (meth.getName(seargeNamespace) in methods) {
                                val method = methods[meth.getName(seargeNamespace)]!!
                                visitor.visitField(method.source, meth.getDesc(seargeNamespace))
                                visitor.visitDstName(MappedElementKind.FIELD, 0, method.target)
                                visitor.visitElementContent(MappedElementKind.FIELD)
                                if (!method.desc.isNullOrEmpty() && method.desc != "*") {
                                    visitor.visitComment(MappedElementKind.FIELD, method.desc)
                                }
                            }
                        }
                    }
                }
            }
        }

        visitor.visitEnd()

        visitor.accept(parentVisitor)
    }


    private data class MethodData(
        val source: String,
        val target: String,
        val desc: String?
    )

    private data class FieldData(
        val source: String,
        val target: String,
        val desc: String?
    )

}
