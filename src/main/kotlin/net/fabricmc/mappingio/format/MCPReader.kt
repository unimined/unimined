package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.io.Reader


private fun ColumnFileReader.readCell(): String {
    var source = nextCol()
    if (source.startsWith("\"")) {
        while (!source.endsWith("\"")) source += nextCol() ?: throw IllegalStateException("String not closed at line $lineNumber")
        source = source.substring(1, source.length - 1)
    }
    return source
}

object MCPReader {
    fun readMethod(reader: Reader, visitor: MemoryMappingTree) {
        readMethod(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readMethod(reader: Reader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        readMethod(ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, visitor)
    }

    private fun readMethod(
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {


        val methods = mutableMapOf<String, MethodData>()
        while (reader.nextLine(0)) {
            val src = reader.readCell()
            methods[src] = MethodData(src, reader.readCell(), reader.readCell(), reader.readCell())
        }

        val parentVisitor = visitor
        val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        val seargeNamespace = visitor.getNamespaceId(sourceNamespace)
        if (seargeNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $sourceNamespace not found")
        }

        var visitLastClass: Boolean

        if (visitor.visitContent()) {
            for (clazz in (parentVisitor as MappingTreeView).classes) {
                visitLastClass = visitor.visitClass(clazz.getName(seargeNamespace))
                if (visitLastClass) {
                    visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)

                    if (visitLastClass) {
                        for (meth in clazz.methods) {
                            if (meth.getName(seargeNamespace) in methods) {
                                val method = methods[meth.getName(seargeNamespace)]!!
                                visitor.visitMethod(method.source, meth.getDesc(seargeNamespace))
                                visitor.visitDstName(MappedElementKind.METHOD, 0, method.target)
                                visitor.visitElementContent(MappedElementKind.METHOD)
                                if (method.desc != null) {
                                    visitor.visitComment(MappedElementKind.METHOD, method.desc)
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

    fun readField(reader: Reader, visitor: MemoryMappingTree) {
        readField(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readField(reader: Reader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        readField(ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, visitor)
    }

    private fun readField(
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {
        val fields = mutableMapOf<String, FieldData>()
        while (reader.nextLine(0)) {
            val src = reader.readCell()
            fields[src] = FieldData(src, reader.readCell(), reader.readCell(), reader.readCell())
        }

        val parentVisitor = visitor
        val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        val seargeNamespace = visitor.getNamespaceId(sourceNamespace)
        if (seargeNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $sourceNamespace not found")
        }

        var visitLastClass: Boolean

        if (visitor.visitContent()) {
            for (clazz in (parentVisitor as MappingTreeView).classes) {
                visitLastClass = visitor.visitClass(clazz.getName(seargeNamespace))
                if (visitLastClass) {
                    visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)

                    if (visitLastClass) {
                        for (meth in clazz.fields) {
                            if (meth.getName(seargeNamespace) in fields) {
                                val field = fields[meth.getName(seargeNamespace)]!!
                                visitor.visitField(field.source, meth.getDesc(seargeNamespace))
                                visitor.visitDstName(MappedElementKind.FIELD, 0, field.target)
                                visitor.visitElementContent(MappedElementKind.FIELD)
                                if (field.desc != null) {
                                    visitor.visitComment(MappedElementKind.FIELD, field.desc)
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

    fun readParam(reader: Reader, visitor: MemoryMappingTree) {
        readParam(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readParam(reader: Reader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        readParam(ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, visitor)
    }

    private fun readParam(
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {
        val params = mutableMapOf<String, ParamData>()
        while (reader.nextLine(0)) {
            val src = reader.readCell().split("_")
            params[src[1]] = ParamData(src[2], reader.readCell(), reader.readCell())
        }

        val parentVisitor = visitor
        val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        val seargeNamespace = visitor.getNamespaceId(sourceNamespace)
        if (seargeNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $sourceNamespace not found")
        }

        var visitLastClass: Boolean

        if (visitor.visitContent()) {
            for (clazz in (parentVisitor as MappingTreeView).classes) {
                visitLastClass = visitor.visitClass(clazz.getName(seargeNamespace))
                if (visitLastClass) {
                    visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)

                    if (visitLastClass) {
                        for (meth in clazz.methods) {
                            val srgId = meth.getName(seargeNamespace).split("_")[1]
                            if (srgId in params) {
                                val param = params[srgId]!!
                                visitor.visitMethod(meth.getName(seargeNamespace), meth.getDesc(seargeNamespace))
                                visitor.visitElementContent(MappedElementKind.METHOD)
                                visitor.visitMethodArg(Integer.parseInt(param.source), -1, param.target)
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
        val side: String,
        val desc: String?
    )

    private data class FieldData(
        val source: String,
        val target: String,
        val side: String,
        val desc: String?
    )

    private data class ParamData(
        val source: String,
        val target: String,
        val side: String
    )
}