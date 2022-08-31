package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import java.io.Reader


internal fun ColumnFileReader.readCell(): String? {
    var source: String = nextCol() ?: return null
    if (source.startsWith("\"")) {
        while (!source.endsWith("\"")) source += nextCol() ?: throw IllegalStateException("String not closed at line $lineNumber")
        source = source.substring(1, source.length - 1)
    }
    return source
}

@Suppress("UNUSED")
object MCPReader {

    private fun checkHeader(reader: ColumnFileReader): Boolean {
        return (reader.nextCol("searge") || reader.nextCol("param")) && reader.nextCol("name") && reader.nextCol("side")
    }

    private fun checkPackageHeader(reader: ColumnFileReader): Boolean {
        return reader.nextCol("class") && reader.nextCol("package")
    }

    fun readMethod(envType: EnvType, reader: Reader, visitor: MemoryMappingTree) {
        readMethod(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readMethod(envType: EnvType, reader: Reader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        readMethod(envType, ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, visitor)
    }

    private fun readMethod(
        envType: EnvType,
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {

        reader.mark()
        if (!checkHeader(reader)) {
            reader.reset()
            throw IllegalStateException("Invalid header")
        }

        val methods = mutableMapOf<String, MethodData>()
        while (reader.nextLine(0)) {
            val src = reader.readCell()!!
            val data = MethodData(src, reader.readCell()!!, reader.readCell()!!, reader.readCell())
            if (data.side != "2" && data.side.toInt() != envType.ordinal) continue
            methods[src] = data
        }

        val parentVisitor = visitor
        @Suppress("NAME_SHADOWING")
        val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        val seargeNamespace = parentVisitor.getNamespaceId(sourceNamespace)
        if (seargeNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $sourceNamespace not found")
        }


        if (visitor.visitContent()) {
            var visitLastClass: Boolean

            for (clazz in (parentVisitor as MappingTreeView).classes) {
                val cn = clazz.getName(seargeNamespace)
                visitLastClass = visitor.visitClass(cn)

                if (visitLastClass) {
                    visitor.visitDstName(MappedElementKind.CLASS, 0, cn)
                    visitor.visitElementContent(MappedElementKind.CLASS)
                }
            }

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

    fun readField(envType: EnvType, reader: Reader, visitor: MemoryMappingTree) {
        readField(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readField(envType: EnvType, reader: Reader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        readField(envType, ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, visitor)
    }

    private fun readField(
        envType: EnvType,
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {

        reader.mark()
        if (!checkHeader(reader)) {
            reader.reset()
            throw IllegalStateException("Invalid header")
        }

        val fields = mutableMapOf<String, FieldData>()
        while (reader.nextLine(0)) {
            val src = reader.readCell()!!
            val data = FieldData(src, reader.readCell()!!, reader.readCell()!!, reader.readCell())
            if (data.side != "2" && data.side.toInt() != envType.ordinal) continue
            fields[src] = data
        }

        val parentVisitor = visitor
        @Suppress("NAME_SHADOWING")
        val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        val seargeNamespace = parentVisitor.getNamespaceId(sourceNamespace)
        if (seargeNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $sourceNamespace not found")
        }

        var visitLastClass: Boolean


        if (visitor.visitContent()) {
            for (clazz in (parentVisitor as MappingTreeView).classes) {
                val cn = clazz.getName(seargeNamespace)
                visitLastClass = visitor.visitClass(cn)

                if (visitLastClass) {
                    visitor.visitDstName(MappedElementKind.CLASS, 0, cn)
                    visitor.visitElementContent(MappedElementKind.CLASS)
                }
            }

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

    fun readParam(envType: EnvType, reader: Reader, visitor: MemoryMappingTree) {
        readParam(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readParam(envType: EnvType, reader: Reader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        readParam(envType, ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, visitor)
    }

    private fun readParam(
        envType: EnvType,
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {

        reader.mark()
        if (!checkHeader(reader)) {
            reader.reset()
            throw IllegalStateException("Invalid header")
        }

        val params = mutableMapOf<String, ParamData>()
        while (reader.nextLine(0)) {
            val src = reader.readCell()!!.split("_")
            val data = ParamData(src[2], reader.readCell()!!, reader.readCell()!!)
            if (data.side != "2" && data.side.toInt() != envType.ordinal) continue
            params[src[1]] = data
        }

        val parentVisitor = visitor
        @Suppress("NAME_SHADOWING") val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        val seargeNamespace = parentVisitor.getNamespaceId(sourceNamespace)
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
                            val srg = meth.getName(seargeNamespace).split("_")
                            if (srg.size < 2) continue
                            val srgId = srg[1]
                            if (srgId in params) {
                                val param = params[srgId]!!
                                visitor.visitMethod(meth.getName(seargeNamespace), meth.getDesc(seargeNamespace))
                                visitor.visitElementContent(MappedElementKind.METHOD)
                                if (visitor.visitMethodArg(-1, Integer.parseInt(param.source), null)) {
                                    visitor.visitDstName(MappedElementKind.METHOD_ARG, 0, param.target)
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

    fun readPackages(envType: EnvType, reader: Reader, visitor: MemoryMappingTree) {
        readPackages(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readPackages(envType: EnvType, reader: Reader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        readPackages(envType, ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, visitor)
    }

    private fun readPackages(
        envType: EnvType,
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {
        reader.mark()
        if (!checkPackageHeader(reader)) {
            reader.reset()
            throw IllegalStateException("Invalid header")
        }

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        var visitLastClass: Boolean
        if (visitor.visitContent()) {
            while (reader.nextLine(0)) {
                val src = reader.readCell()
                visitLastClass = visitor.visitClass("net/minecraft/src/$src")

                if (visitLastClass) {
                    visitor.visitDstName(MappedElementKind.CLASS, 0, reader.readCell() + "/" + src)
                    visitor.visitElementContent(MappedElementKind.CLASS)
                }
            }
        }

        visitor.visitEnd()
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