package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingFlag
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.io.IOException
import java.io.Reader

@Suppress("UNUSED")
object RGSReader {
    fun read(reader: Reader, visitor: MappingVisitor) {
        read(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }

    fun read(reader: Reader, sourceNs: String, targetNs: String, visitor: MappingVisitor) {
        read(ColumnFileReader(reader, ' '), sourceNs, targetNs, visitor)
    }

    private fun read(reader: ColumnFileReader, sourceNs: String, targetNs: String, visitor: MappingVisitor) {
        val flags = visitor.flags
        var parentVisitor: MappingVisitor? = null

        @Suppress("NAME_SHADOWING")
        var visitor = visitor
        if (flags.contains(MappingFlag.NEEDS_UNIQUENESS)) {
            parentVisitor = visitor
            visitor = MemoryMappingTree()
        } else if (flags.contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
            reader.mark()
        }

        while (true) {
            var visitHeader = visitor.visitHeader()

            if (visitHeader) {
                visitor.visitNamespaces(sourceNs, listOf(targetNs))
            }

            if (visitor.visitContent()) {
                var lastClass: String? = null
                var visitLastClass = false

                do {

                    if (reader.nextCol(".class_map")) {
                        val srcName = reader.nextCol()
                        if (srcName.contains("$")) {
                            continue
                        }
                        if (!srcName.equals(lastClass)) {
                            lastClass = srcName
                            visitLastClass = visitor.visitClass(srcName)

                            if (visitLastClass) {
                                val dstName = reader.nextCol()
                                if (dstName == null || dstName.isEmpty()) throw IOException("missing class-name-b in line ${reader.lineNumber}")

                                visitor.visitDstName(MappedElementKind.CLASS, 0, "net/minecraft/src/$dstName")
                                visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)
                            }
                        }
                    } else if (reader.nextCol(".method_map")) {
                        val src = reader.nextCol()
                        if (src == null) throw IOException("missing method-name-a in line ${reader.lineNumber}")

                        val srcSepPos = src.lastIndexOf('/')
                        if (srcSepPos <= 0 || srcSepPos == src.length - 1) throw IOException("invalid method-name-a in line ${reader.lineNumber}")

                        val srcDesc = reader.nextCol()
                        if (srcDesc == null) throw IOException("missing method-desc-a in line ${reader.lineNumber}")

                        val dst = reader.nextCol()
                        if (dst == null) throw IOException("missing method-name-b in line ${reader.lineNumber}")

                        val srcOwner = src.substring(0, srcSepPos)
                        if (srcOwner != lastClass) {
                            lastClass = srcOwner
                            visitLastClass = visitor.visitClass(srcOwner)

                            if (visitLastClass) {
                                visitor.visitDstName(MappedElementKind.CLASS, 0, srcOwner)
                                visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)
                            }
                        }

                        if (visitLastClass) {
                            val srcName = src.substring(srcSepPos + 1)

                            if (visitor.visitMethod(srcName, srcDesc)) {
                                visitor.visitDstName(MappedElementKind.METHOD, 0, dst)
                                visitor.visitElementContent(MappedElementKind.METHOD)
                            }
                        }
                    } else if (reader.nextCol(".field_map")) {
                        val src = reader.nextCol()
                        if (src == null) throw IOException("missing field-name-a in line ${reader.lineNumber}")

                        val srcSepPos = src.lastIndexOf('/')
                        if (srcSepPos <= 0 || srcSepPos == src.length - 1) throw IOException("invalid field-name-a in line ${reader.lineNumber}")

                        val dst = reader.nextCol()
                        if (dst == null) throw IOException("missing field-name-b in line ${reader.lineNumber}")

                        val srcOwner = src.substring(0, srcSepPos)
                        if (srcOwner != lastClass) {
                            lastClass = srcOwner
                            visitLastClass = visitor.visitClass(srcOwner)

                            if (visitLastClass) {
                                visitor.visitDstName(MappedElementKind.CLASS, 0, srcOwner)
                                visitLastClass = visitor.visitElementContent(MappedElementKind.CLASS)
                            }
                        }

                        if (visitLastClass) {
                            val srcName = src.substring(srcSepPos + 1)

                            if (visitor.visitField(srcName, null)) {
                                visitor.visitDstName(MappedElementKind.FIELD, 0, dst)
                                visitor.visitElementContent(MappedElementKind.FIELD)
                            }
                        }

                    }
                } while (reader.nextLine(0))
            }

            if (visitor.visitEnd()) break

            reader.reset()
        }

        if (parentVisitor != null) {
            (visitor as MappingTree).accept(parentVisitor)
        }
    }
}