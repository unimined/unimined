package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.tree.MemoryMappingTree

@Suppress("UNUSED", "UNUSED_PARAMETER")
object OlderMCPReader {
    internal fun readMethod(reader: ColumnFileReader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        TODO()
    }

    internal fun readParam(reader: ColumnFileReader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        throw UnsupportedOperationException("Older MCPReader does not support reading param mappings")
    }

    internal fun readField(reader: ColumnFileReader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
        TODO()
    }

}
