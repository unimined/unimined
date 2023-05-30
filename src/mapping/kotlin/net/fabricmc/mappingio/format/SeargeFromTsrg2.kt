package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree

object SeargeFromTsrg2 {

    fun apply(tsrgNamespace: String, classNamesNamespace: String, seargeNamespace: String, tree: MappingTreeView, visitor: MappingVisitor) {
        val tsrgId = tree.getNamespaceId(tsrgNamespace)
        val classNamesId = tree.getNamespaceId(classNamesNamespace)

        if (tsrgId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalArgumentException("Namespace $tsrgNamespace not found")
        }

        if (classNamesId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalArgumentException("Namespace $classNamesNamespace not found")
        }

        val visitHeader = visitor.visitHeader()

        if (visitHeader) {
            visitor.visitNamespaces(tsrgNamespace, listOf(seargeNamespace))
        }

        if (visitor.visitContent()) {
            for (c in tree.classes) {
                val tsrgName = c.getName(tsrgId)
                if (tsrgName != null) {
                    if (visitor.visitClass(tsrgName)) {
                        visitor.visitDstName(MappedElementKind.CLASS, 0, c.getName(classNamesId) ?: tsrgName)
                        if (visitor.visitElementContent(MappedElementKind.CLASS)) {
                            for (m in c.methods) {
                                val tsrgMethodName = m.getName(tsrgId)
                                val tsrgMethodDesc = m.getDesc(tsrgId)
                                if (tsrgMethodName != null) {
                                    if (visitor.visitMethod(tsrgMethodName, tsrgMethodDesc)) {
                                        visitor.visitDstName(MappedElementKind.METHOD, 0, tsrgMethodName)
                                        visitor.visitElementContent(MappedElementKind.METHOD)
                                    }
                                }
                            }
                            for (f in c.fields) {
                                val tsrgFieldName = f.getName(tsrgId)
                                val tsrgFieldDesc = f.getDesc(tsrgId)
                                if (tsrgFieldName != null) {
                                    if (visitor.visitField(tsrgFieldName, tsrgFieldDesc)) {
                                        visitor.visitDstName(MappedElementKind.FIELD, 0, tsrgFieldName)
                                        visitor.visitElementContent(MappedElementKind.FIELD)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        visitor.visitEnd()
    }
}