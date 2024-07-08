package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingWriter
import java.io.Writer

class TsrgV1Writer(val writer: Writer): MappingWriter {
    override fun close() {
        writer.close()
    }

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        if (dstNamespaces.size != 1) {
            throw IllegalArgumentException("SrgWriter only supports one destination namespace")
        }
    }

    private lateinit var clazz: String

    override fun visitClass(srcName: String): Boolean {
        clazz = srcName
        return true
    }

    private lateinit var field: String
    private var fieldName: String? = null

    override fun visitField(srcName: String, srcDesc: String?): Boolean {
        field = srcName
        fieldName = null
        return true
    }

    private lateinit var method: String
    private lateinit var methodDesc: String
    private var methodName: String? = null
    private var methodDescName: String? = null

    override fun visitMethod(srcName: String, srcDesc: String): Boolean {
        method = srcName
        methodDesc = srcDesc
        methodName = null
        methodDescName = null
        return true
    }

    override fun visitMethodArg(argPosition: Int, lvIndex: Int, srcName: String?): Boolean {
        // noop
        return true
    }

    override fun visitMethodVar(lvtRowIndex: Int, lvIndex: Int, startOpIdx: Int, srcName: String?): Boolean {
        // noop
        return true
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String) {
        when (targetKind) {
            MappedElementKind.CLASS -> {
                writer.write("$clazz $name\n")
            }

            MappedElementKind.FIELD -> {
                fieldName = name
                writer.write("\t$field $name\n")
            }

            MappedElementKind.METHOD -> {
                writer.write("\t$method $methodDesc $name\n")
            }

            else -> {}
        }
    }

    override fun visitComment(targetKind: MappedElementKind, comment: String) {
        // noop
    }
}