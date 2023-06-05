package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappingFlag
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.util.forEachInZip
import java.nio.file.Path

object BytecodeToMappings {

    fun readFile(path: Path, visitor: MappingVisitor) {
        readFile(path, MappingUtil.NS_SOURCE_FALLBACK, visitor)
    }

    fun readFile(path: Path, sourceNs: String, visitor: MappingVisitor) {
        val flags = visitor.flags
        var parentVisitor: MappingVisitor? = null

        @Suppress("NAME_SHADOWING")
        var visitor = visitor
        if (flags.contains(MappingFlag.NEEDS_UNIQUENESS) || flags.contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
            parentVisitor = visitor
            visitor = MemoryMappingTree()
        }

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(sourceNs, listOf())
        }

        if (visitor.visitContent()) {
            path.forEachInZip { s, inputStream ->
                if (s.endsWith(".class")) {
                    val cNode = ClassNode()
                    ClassReader(inputStream).accept(cNode, ClassReader.SKIP_CODE)
                    if (visitor.visitClass(cNode.name)) {
                        cNode.methods.forEach { method ->
                            visitor.visitMethod(method.name, method.desc)
                        }
                        cNode.fields.forEach { field ->
                            visitor.visitField(field.name, field.desc)
                        }
                    }
                }
            }
        }
        visitor.visitEnd()

        if (parentVisitor != null) {
            (visitor as MappingTree).accept(parentVisitor)
        }
    }

}