package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.io.Reader

object MCPReader {
     fun readMethod(reader: Reader, visitor: MemoryMappingTree) {
         readMethod(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor);
     }

     fun readMethod(reader: Reader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
         readMethod(ColumnFileReader(reader, ','), sourceNamespace, targetNamespace, visitor);
     }

     private fun readMethod(reader: ColumnFileReader, sourceNamespace: String, targetNamespace: String, visitor: MemoryMappingTree) {
         TODO()
         visitor.visitNamespaces("srg", listOf("mcp"))
         while (reader.nextLine(0)) {
            System.out.println(reader.nextCol())
         }
     }
}