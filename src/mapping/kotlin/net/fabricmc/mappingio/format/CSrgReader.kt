package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingFlag
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.io.IOException
import java.io.Reader

object CSrgReader {

    fun readClasses(reader: Reader, visitor: MappingVisitor) {
        readClasses(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor)
    }
    fun readClasses(reader: Reader, sourceNs: String, targetNs: String, visitor: MappingVisitor) {
        readClasses(ColumnFileReader(reader, ' '), sourceNs, targetNs, visitor)
    }

    private fun readClasses(reader: ColumnFileReader, sourceNs: String, targetNs: String, visitor: MappingVisitor) {
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

                do {
                    val srcName = reader.nextCol()
                    if (srcName.contains("#")) continue
                    var dstName: String = reader.nextCol() ?: continue
                    if (dstName.contains("#")) {
                        dstName = dstName.substring(0, dstName.indexOf('#'))
                    }
                    if (!srcName.equals(lastClass)) {
                        lastClass = srcName
                        if (visitor.visitClass(srcName)) {
                            visitor.visitDstName(MappedElementKind.CLASS, 0, dstName)

                            visitor.visitElementContent(MappedElementKind.CLASS)
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

    fun readMembers(reader: Reader, visitor: MappingVisitor, mappingTree: MappingTreeView) {
        readMembers(reader, MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK, visitor, mappingTree)
    }

    fun readMembers(reader: Reader, sourceNs: String, targetNs: String, visitor: MappingVisitor, mappingTree: MappingTreeView) {
        readMembers(ColumnFileReader(reader, ' '), sourceNs, targetNs, visitor, mappingTree)
    }

    private fun readMembers(reader: ColumnFileReader, sourceNs: String, targetNs: String, visitor: MappingVisitor, mappingTree: MappingTreeView) {
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

        val sourceNsId = mappingTree.getNamespaceId(sourceNs)
        if (sourceNsId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $sourceNs not found")
        }

        val targetNsId = mappingTree.getNamespaceId(targetNs)
        if (targetNsId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $targetNs not found")
        }

        while (true) {
            var visitHeader = visitor.visitHeader()

            if (visitHeader) {
                visitor.visitNamespaces(sourceNs, listOf(targetNs))
            }

            if (visitor.visitContent()) {
                do {
                    val className = reader.nextCol()
                    if (className.contains("#")) continue
                    val srcName: String = reader.nextCol() ?: continue
                    if (srcName.contains("#")) continue
                    val dstNameOrDesc = reader.nextCol() ?: continue
                    // find un-named name of class
                    val srcClassName = mappingTree.getClass(className, targetNsId)?.getName(sourceNsId) ?: className
                    if (srcClassName == className) System.err.println("Warning: class $className not found in mapping tree")
                    if (visitor.visitClass(srcClassName) && visitor.visitElementContent(MappedElementKind.CLASS)) {
                        if (dstNameOrDesc.contains("(")) {
                            // method
                            if (dstNameOrDesc.contains("#")) continue
                            var dstName = reader.nextCol() ?: continue
                            if (dstName.contains("#")) {
                                dstName = dstName.substring(0, dstName.indexOf('#'))
                            }
                            if (visitor.visitMethod(srcName, dstNameOrDesc)) {
                                visitor.visitDstName(MappedElementKind.METHOD, 0, dstName)
                            }
                        } else {
                            // field
                            var dstName = dstNameOrDesc
                            if (dstName.contains("#")) {
                                dstName = dstName.substring(0, dstName.indexOf('#'))
                            }
                            if (visitor.visitField(srcName, null)) {
                                visitor.visitDstName(MappedElementKind.FIELD, 0, dstName)
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

    fun readPackages(reader: Reader): (MappingVisitor) -> PackageRemappingVisitor {
        return readPackages(reader, setOf(MappingUtil.NS_SOURCE_FALLBACK, MappingUtil.NS_TARGET_FALLBACK))
    }

    fun readPackages(reader: Reader, targetNamespaces: Set<String>): (MappingVisitor) -> PackageRemappingVisitor {
        return readPackages(ColumnFileReader(reader, ' '), targetNamespaces)
    }

    private fun readPackages(reader: ColumnFileReader, targetNamespaces: Set<String>): (MappingVisitor) -> PackageRemappingVisitor {
        reader.mark()

        val packages = mutableListOf<Pair<String, String>>()
        do {
            val srcPackage = reader.nextCol() ?: continue
            if (srcPackage.contains("#")) continue
            var dstPackage = reader.nextCol() ?: break
            if (dstPackage.contains("#")) {
                dstPackage = dstPackage.substring(0, dstPackage.indexOf('#'))
            }
            if (dstPackage.endsWith("/")) {
                dstPackage = dstPackage.substring(0, dstPackage.length - 1)
            }
            packages += "$srcPackage*" to dstPackage
        } while (reader.nextLine(0))

        return { visitor ->
            PackageRemappingVisitor(visitor, targetNamespaces, packages)
        }
    }


}