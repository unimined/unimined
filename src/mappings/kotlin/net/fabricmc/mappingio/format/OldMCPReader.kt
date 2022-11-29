package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import java.io.Reader

@Suppress("UNUSED")
object OldMCPReader {

    private fun checkHeader(reader: ColumnFileReader): Boolean {
        return reader.nextCol("\"searge\"") && reader.nextCol("\"name\"") && reader.nextCol("\"notch\"") && reader.nextCol(
            "\"sig\""
        ) && reader.nextCol("\"notchsig\"") && reader.nextCol("\"classname\"") && reader.nextCol("\"classnotch\"") && reader.nextCol(
            "\"package\""
        ) && reader.nextCol("\"side\"")
    }

    private fun checkClassesHeader(reader: ColumnFileReader): Boolean {
        return reader.nextCol("\"name\"") && reader.nextCol("\"notch\"") && reader.nextCol("\"supername\"") && reader.nextCol(
            "\"package\""
        ) && reader.nextCol("\"side\"")
    }

    fun readMethod(envType: EnvType, reader: Reader, visitor: MemoryMappingTree) {
        readMethod(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, "searge", MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readMethod(
        envType: EnvType,
        reader: Reader,
        notchNamespace: String,
        seargeNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {
        readMethod(envType, ColumnFileReader(reader, ','), notchNamespace, seargeNamespace, targetNamespace, visitor)
    }

    internal fun readMethod(
        envType: EnvType,
        reader: ColumnFileReader,
        notchNamespace: String,
        seargeNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {

        reader.mark()
        if (!checkHeader(reader)) {
            reader.reset()
            throw IllegalArgumentException("Invalid header")
        }

        val parentVisitor = visitor

        @Suppress("NAME_SHADOWING") val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(notchNamespace, listOf(seargeNamespace, targetNamespace))
        }

        val notchNamespaceId = parentVisitor.getNamespaceId(notchNamespace)
        if (notchNamespaceId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $notchNamespace not found")
        }

        if (visitor.visitContent()) {
            var lastClass: String? = null
            var visitLastClass = false

            while (reader.nextLine(0)) {
                val searge = reader.readCell()
                val name = reader.readCell()
                val notch = reader.readCell()
                val sig = reader.readCell()!!
                var notchSig = reader.readCell()!!
                val className = reader.readCell()
                var classNotch = reader.readCell()
                val packageName = reader.readCell()
                val side = reader.readCell()!!
                if (side != "2" && side.toInt() != envType.ordinal) continue

                if (className == classNotch) {
                    classNotch = "$packageName/$classNotch"
                }

                notchSig = fixNotchSig(notchSig, sig, parentVisitor)

                if (lastClass != classNotch) {
                    lastClass = classNotch
                    visitLastClass = visitor.visitClass(classNotch)

                    if (visitLastClass) {
                        visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)
                    }
                }
                if (visitLastClass) {
                    visitor.visitMethod(notch, notchSig)
                    visitor.visitDstName(MappedElementKind.METHOD, 0, searge)
                    visitor.visitDstName(MappedElementKind.METHOD, 1, name)
                    visitor.visitElementContent(MappedElementKind.METHOD)
                }
            }
        }

        visitor.visitEnd()

        visitor.accept(parentVisitor)
    }

    fun readField(envType: EnvType, reader: Reader, visitor: MemoryMappingTree) {
        readField(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, "searge", MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readField(
        envType: EnvType,
        reader: Reader,
        notchNamespace: String,
        seargeNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {
        readField(envType, ColumnFileReader(reader, ','), notchNamespace, seargeNamespace, targetNamespace, visitor)
    }

    internal fun readField(
        envType: EnvType,
        reader: ColumnFileReader,
        notchNamespace: String,
        seargeNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {

        reader.mark()
        if (!checkHeader(reader)) {
            reader.reset()
            throw IllegalArgumentException("Invalid header")
        }

        val parentVisitor = visitor

        @Suppress("NAME_SHADOWING") val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(notchNamespace, listOf(seargeNamespace, targetNamespace))
        }

        val notchNamespaceId = parentVisitor.getNamespaceId(notchNamespace)
        if (notchNamespaceId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $notchNamespace not found")
        }

        if (visitor.visitContent()) {
            var lastClass: String? = null
            var visitLastClass = false

            while (reader.nextLine(0)) {
                val searge = reader.readCell()
                val name = reader.readCell()
                val notch = reader.readCell()
                val sig = reader.readCell()!!
                var notchSig = reader.readCell()!!
                val className = reader.readCell()
                var classNotch = reader.readCell()
                val packageName = reader.readCell()
                val side = reader.readCell()!!
                if (side != "2" && side.toInt() != envType.ordinal) continue

                if (className == classNotch) {
                    classNotch = "$packageName/$classNotch"
                }

                notchSig = fixNotchSig(notchSig, sig, parentVisitor)

                if (lastClass != classNotch) {
                    lastClass = classNotch
                    visitLastClass = visitor.visitClass(classNotch)

                    if (visitLastClass) {
                        visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)
                    }
                }
                if (visitLastClass) {
                    visitor.visitField(notch, notchSig)
                    visitor.visitDstName(MappedElementKind.FIELD, 0, searge)
                    visitor.visitDstName(MappedElementKind.FIELD, 1, name)
                    visitor.visitElementContent(MappedElementKind.FIELD)
                }
            }
        }

        visitor.visitEnd()

        visitor.accept(parentVisitor)
    }

    private val classSigRegex = Regex("L([^;]+);")

    private fun fixNotchSig(notchSig: String, sig: String, parentVisitor: MappingTreeView): String {
        @Suppress("NAME_SHADOWING") var notchSig = notchSig
        val notchSigSpl = classSigRegex.findAll(notchSig).map { it.groupValues[1] }.toList()
        val sigSpl = classSigRegex.findAll(sig).map { it.groupValues[1] }.toList()
        try {
            for (i in notchSigSpl.indices) {
                if (notchSigSpl[i] == sigSpl[i]) {
                    // find in class map
                    var found = false
                    for (clazz in parentVisitor.classes) {
                        if (clazz.srcName.split("/").last() == notchSigSpl[i]) {
                            notchSig = notchSig.replace(notchSigSpl[i], clazz.srcName)
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        System.err.println("Class not found: ${notchSigSpl[i]}")
                    }
                }
            }
        } catch (oob: IndexOutOfBoundsException) {
            System.err.println("Notch sig: $notchSig")
            System.err.println("Sig: $sig")
        }
        return notchSig
    }

    fun readParam(envType: EnvType, reader: Reader, visitor: MemoryMappingTree) {
        readParam(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readParam(
        envType: EnvType, reader: Reader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree
    ) {
        readParam(envType, ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, visitor)
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun readParam(
        envType: EnvType,
        reader: ColumnFileReader,
        sourceNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {
        throw UnsupportedOperationException("Old MCPReader does not support reading param mappings")
    }

    fun readClasses(envType: EnvType, reader: Reader, visitor: MemoryMappingTree) {
        readClasses(envType, reader, MappingUtil.NS_SOURCE_FALLBACK, "searge", MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun readClasses(
        envType: EnvType,
        reader: Reader,
        notchNamespace: String,
        seargeNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {
        readClasses(envType, ColumnFileReader(reader, ','), notchNamespace, seargeNamespace, targetNamespace, visitor)
    }

    private fun readClasses(
        envType: EnvType,
        reader: ColumnFileReader,
        notchNamespace: String,
        seargeNamespace: String,
        targetNamespace: String,
        visitor: MemoryMappingTree
    ) {
        reader.mark()
        if (!checkClassesHeader(reader)) {
            reader.reset()
            throw IllegalArgumentException("Invalid header")
        }

        val parentVisitor = visitor

        @Suppress("NAME_SHADOWING") val visitor = MemoryMappingTree()

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(notchNamespace, listOf(seargeNamespace, targetNamespace))
        }

        val notchNamespaceId = visitor.getNamespaceId(notchNamespace)

        if (notchNamespaceId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $notchNamespace not found")
        }

        var visitLastClass: Boolean

        if (visitor.visitContent()) {
            while (reader.nextLine(0)) {
                val name = reader.readCell()
                val notch = reader.readCell()
                reader.readCell() // superName
                val packageName = reader.readCell()
                val side = reader.readCell()!!
                if (side != "2" && side.toInt() != envType.ordinal) continue

                if (name == notch) continue

                visitLastClass = visitor.visitClass(notch)

                if (visitLastClass) {
                    visitor.visitDstName(MappedElementKind.CLASS, 0, "$packageName/$name")
                    visitor.visitDstName(MappedElementKind.CLASS, 1, "$packageName/$name")
                    visitor.visitElementContent(MappedElementKind.CLASS)
                }
            }
        }

        visitor.visitEnd()

        visitor.accept(parentVisitor)
    }
}