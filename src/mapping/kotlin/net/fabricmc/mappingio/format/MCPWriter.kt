package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingWriter
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MCPWriter(writer: OutputStream, private val side: Int): MappingWriter {
    private val writer = ZipOutputStream(writer)
    private val fields = StringBuilder().append("searge,name,side,desc")
    private val methods = StringBuilder().append("searge,name,side,desc")
    private var lastField: String? = null
    private var lastFieldDst: Boolean = false
    private var lastMethod: String? = null
    private var lastMethodDst: Boolean = false

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        if (dstNamespaces.size != 1) throw UnsupportedOperationException("MCP only supports one destination namespace")
    }

    override fun visitClass(srcName: String?): Boolean {
        return true
    }

    override fun visitField(srcName: String, srcDesc: String?): Boolean {
        lastField = "\n$srcName,"
        lastFieldDst = false
        return true
    }

    override fun visitMethod(srcName: String, srcDesc: String?): Boolean {
        lastMethod = "\n$srcName,"
        lastMethodDst = false
        return true
    }

    override fun visitMethodArg(argPosition: Int, lvIndex: Int, srcName: String?): Boolean {
        return true
    }

    override fun visitMethodVar(lvtRowIndex: Int, lvIndex: Int, startOpIdx: Int, endOpIdx: Int, srcName: String?): Boolean {
        return true
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String) {
        when (targetKind) {
            MappedElementKind.FIELD -> {
                lastField += "$name,$side"
                lastFieldDst = true
                fields.append(lastField)
            }

            MappedElementKind.METHOD -> {
                lastMethod += "$name,$side"
                lastMethodDst = true
                methods.append(lastMethod)
            }

            else -> {}
        }
    }

    override fun visitComment(targetKind: MappedElementKind, comment: String) {
        when (targetKind) {
            MappedElementKind.FIELD -> {
                if (lastFieldDst)
                    fields.append(",\"").append(comment.replace("\"", "\"\"")).append("\"")
            }

            MappedElementKind.METHOD -> {
                if (lastMethodDst)
                    methods.append(",\"").append(comment.replace("\"", "\"\"")).append("\"")
            }

            else -> {}
        }
    }

    override fun close() {
        writer.putNextEntry(ZipEntry("fields.csv"))
        writer.write(fields.toString().toByteArray())
        writer.closeEntry()
        writer.putNextEntry(ZipEntry("methods.csv"))
        writer.write(methods.toString().toByteArray())
        writer.closeEntry()
        writer.close()
    }


}