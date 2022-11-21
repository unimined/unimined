package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingWriter
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MCPWriter(writer: OutputStream, private val side: Int) : MappingWriter {
    private val writer = ZipOutputStream(writer)
    private val fields = StringBuilder().append("searge,name,side,desc")
    private val methods = StringBuilder().append("searge,name,side,desc")

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        if (dstNamespaces.size != 1) throw UnsupportedOperationException("MCP only supports one destination namespace")
    }

    override fun visitClass(srcName: String?): Boolean {
        return true
    }

    override fun visitField(srcName: String, srcDesc: String?): Boolean {
        fields.append("\n").append(srcName).append(",")
        return true
    }

    override fun visitMethod(srcName: String, srcDesc: String?): Boolean {
        methods.append("\n").append(srcName).append(",")
        return true
    }

    override fun visitMethodArg(argPosition: Int, lvIndex: Int, srcName: String): Boolean {
        return true
    }

    override fun visitMethodVar(lvtRowIndex: Int, lvIndex: Int, startOpIdx: Int, srcName: String?): Boolean {
        return true
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String) {
        when (targetKind) {
            MappedElementKind.FIELD -> fields.append(name).append(",").append(side)
            MappedElementKind.METHOD -> methods.append(name).append(",").append(side)
            else -> {}
        }
    }

    override fun visitComment(targetKind: MappedElementKind, comment: String) {
        when (targetKind) {
            MappedElementKind.FIELD -> fields.append(",\"").append(comment).append("\"")
            MappedElementKind.METHOD -> methods.append(",\"").append(comment).append("\"")
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