package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import java.io.Reader


internal fun ColumnFileReader.readCell(): String? {
    var source: String = nextCol() ?: return null
    if (source.startsWith("\"")) {
        while (!source.endsWith("\"")) source += nextCol()
            ?: throw IllegalStateException("String not closed at line $lineNumber")
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

    fun readMethod(envType: EnvType, reader: Reader, seargeMappings: MappingTreeView, visitor: MappingVisitor) {
        readMethod(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, seargeMappings, visitor)
    }

    fun readMethod(
        envType: EnvType,
        reader: Reader,
        sourceNamespace: String,
        targetNamespace: String,
        seargeMappings: MappingTreeView,
        visitor: MappingVisitor
    ) {
        readMethod(envType, ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, seargeMappings, visitor)
    }

    private fun readMethod(
        envType: EnvType,
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        seargeMappings: MappingTreeView,
        visitor: MappingVisitor
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
            if (envType != EnvType.COMBINED && data.side != "2" && data.side.toInt() != envType.mcp) continue
            methods[src] = data
        }

        val parentVisitor = visitor

        @Suppress("NAME_SHADOWING")
        val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNamespace, listOf(targetNamespace))
        }

        val seargeNamespace = seargeMappings.getNamespaceId(sourceNamespace)
        if (seargeNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $sourceNamespace not found")
        }


        if (visitor.visitContent()) {
            var visitLastClass: Boolean

            for (clazz in seargeMappings.classes) {
                val cn = clazz.getName(seargeNamespace) ?: continue
                visitLastClass = visitor.visitClass(cn)

                if (visitLastClass) {
                    visitor.visitDstName(MappedElementKind.CLASS, 0, cn)
                    visitor.visitElementContent(MappedElementKind.CLASS)
                }
            }

            for (clazz in seargeMappings.classes) {
                visitLastClass = visitor.visitClass(clazz.getName(seargeNamespace) ?: continue)
                if (visitLastClass) {
                    visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)

                    if (visitLastClass) {
                        for (meth in clazz.methods) {
                            if (meth.getName(seargeNamespace) in methods) {
                                val method = methods[meth.getName(seargeNamespace)]!!
                                visitor.visitMethod(method.source, meth.getDesc(seargeNamespace))
//                                System.out.println("Owner ${clazz.getName(seargeNamespace)} Method: ${method.source} -> ${method.target}")
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

    fun readField(envType: EnvType, reader: Reader,
        mappingTree: MappingTreeView, visitor: MemoryMappingTree) {
        readField(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, mappingTree, visitor)
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

        reader.mark()
        if (!checkHeader(reader)) {
            reader.reset()
            throw IllegalStateException("Invalid header")
        }

        val fields = mutableMapOf<String, FieldData>()
        while (reader.nextLine(0)) {
            val src = reader.readCell()!!
            val data = FieldData(src, reader.readCell()!!, reader.readCell()!!, reader.readCell())
            if (envType != EnvType.COMBINED && data.side != "2" && data.side.toInt() != envType.mcp) {
//                System.out.println("Skipping ${data.source} ->  ${data.target} (side: ${data.side} env: ${envType.ordinal})")
                continue
            }
            fields[src] = data
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
                        for (fd in clazz.fields) {
                            if (fd.getName(seargeNamespace) in fields) {
                                val field = fields[fd.getName(seargeNamespace)]!!
                                visitor.visitField(field.source, fd.getDesc(seargeNamespace))
//                                System.out.println("Owner ${clazz.getName(seargeNamespace)} Field: ${field.source} -> ${field.target}")
                                visitor.visitDstName(MappedElementKind.FIELD, 0, field.target)
                                visitor.visitElementContent(MappedElementKind.FIELD)
                                if (field.desc != null) {
                                    visitor.visitComment(MappedElementKind.FIELD, field.desc)
                                }
                            } else {
//                                System.out.println("Owner ${clazz.getName(seargeNamespace)} Field: ${fd.getName(seargeNamespace)} -> DNE")
                            }
                        }
                    }
                }
            }
        }

        visitor.visitEnd()

        visitor.accept(parentVisitor)
    }

    fun readParam(envType: EnvType, reader: Reader,
        mappingTree: MappingTreeView, visitor: MappingVisitor) {
        readParam(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, mappingTree, visitor)
    }

    fun readParam(
        envType: EnvType,
        reader: Reader,
        sourceNamespace: String,
        targetNamespace: String,
        mappingTree: MappingTreeView,
        visitor: MappingVisitor
    ) {
        readParam(envType, ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, mappingTree, visitor)
    }

    private fun readParam(
        envType: EnvType,
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        mappingTree: MappingTreeView,
        visitor: MappingVisitor
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
            if (envType != EnvType.COMBINED && data.side != "2" && data.side.toInt() != envType.mcp) continue
            params[src[1]] = data
        }

        val parentVisitor = visitor
        @Suppress("NAME_SHADOWING") val visitor = MemoryMappingTree()

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
                visitLastClass = visitor.visitClass(clazz.getName(seargeNamespace) ?: continue)
                if (visitLastClass) {
                    visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)

                    if (visitLastClass) {
                        for (meth in clazz.methods) {
                            val srg = meth.getName(seargeNamespace)?.split("_") ?: continue
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

    fun readPackages(envType: EnvType, reader: Reader): (MappingVisitor) -> PackageRemappingVisitor {
        return readPackages(envType, reader, setOf(MappingUtil.NS_TARGET_FALLBACK))
    }

    fun readPackages(
        envType: EnvType,
        reader: Reader,
        targetNamespaces: Set<String>
    ): (MappingVisitor) -> PackageRemappingVisitor {
        return readPackages(envType, ColumnFileReader(reader, ','), targetNamespaces)
    }

    private fun readPackages(
        envType: EnvType,
        reader: ColumnFileReader,
        targetNamespaces: Set<String>
    ): (MappingVisitor) -> PackageRemappingVisitor {
        reader.mark()
        if (!checkPackageHeader(reader)) {
            reader.reset()
            throw IllegalStateException("Invalid header")
        }

        val packages = mutableListOf<Pair<String, String>>()
        while (reader.nextLine(0)) {
            val className = reader.readCell()!!
            val packageName = reader.readCell()!!
            packages += "net/minecraft/**/$className" to packageName
        }

        return {
            PackageRemappingVisitor(it, targetNamespaces, packages)
        }
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