package xyz.wagyourtail.unimined.mapping.formats.mcpzip

import okio.BufferedSink
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6FieldWriter
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6MethodWriter
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6ParamWriter
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.Delegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.MultiMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object MCPZipWriter : FormatWriter {
    override fun write(
        append: (String) -> Unit,
        envType: EnvType
    ): MappingVisitor {
        error("Not yet implemented")
    }

    override fun write(into: BufferedSink, envType: EnvType): MappingVisitor {
        val methods = StringBuilder()
        val fields = StringBuilder()
        val params = StringBuilder()
        return MultiMappingVisitor(listOf(
            MCPv6ParamWriter.write(params::append, envType),
            MCPv6FieldWriter.write(fields::append, envType),
            MCPv6MethodWriter.write(methods::append, envType)
        )).delegator(object : Delegator() {
            override fun visitFooter(delegate: MappingVisitor) {
                super.visitFooter(delegate)
                ZipArchiveOutputStream(into.outputStream()).use { zip ->
                    zip.putArchiveEntry(ZipArchiveEntry("methods.csv"))
                    zip.write(methods.toString().toByteArray())
                    zip.closeArchiveEntry()
                    zip.putArchiveEntry(ZipArchiveEntry("fields.csv"))
                    zip.write(fields.toString().toByteArray())
                    zip.closeArchiveEntry()
                    zip.putArchiveEntry(ZipArchiveEntry("params.csv"))
                    zip.write(params.toString().toByteArray())
                    zip.closeArchiveEntry()
                }
            }
        })
    }
}