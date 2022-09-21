package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree

object SeargeFromTsrg2 {

    fun apply(tsrgNamespace: String, classNamesNamespace: String, seargeNamespace: String, tree: MemoryMappingTree) {
        val tsrgId = tree.getNamespaceId(tsrgNamespace)
        val classNamesId = tree.getNamespaceId(classNamesNamespace)

        if (tsrgId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalArgumentException("Namespace $tsrgNamespace not found")
        }

        if (classNamesId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalArgumentException("Namespace $classNamesNamespace not found")
        }

        val visitHeader = tree.visitHeader()

        if (visitHeader) {
            tree.visitNamespaces(tsrgNamespace, listOf(seargeNamespace))
        }

        if (tree.visitContent()) {
            for (c in (tree as MappingTreeView).classes) {
                val tsrgName = c.getName(tsrgId)
                if (tsrgName != null) {
                    if (tree.visitClass(tsrgName)) {
                        tree.visitDstName(MappedElementKind.CLASS, 0, c.getName(classNamesId) ?: tsrgName)
                        if (tree.visitElementContent(MappedElementKind.CLASS)) {
                            for (m in c.methods) {
                                val tsrgMethodName = m.getName(tsrgId)
                                val tsrgMethodDesc = m.getDesc(tsrgId)
                                if (tsrgMethodName != null) {
                                    if (tree.visitMethod(tsrgMethodName, tsrgMethodDesc)) {
                                        tree.visitDstName(MappedElementKind.METHOD, 0, tsrgMethodName)
                                        tree.visitElementContent(MappedElementKind.METHOD)
                                    }
                                }
                            }
                            for (f in c.fields) {
                                val tsrgFieldName = f.getName(tsrgId)
                                val tsrgFieldDesc = f.getDesc(tsrgId)
                                if (tsrgFieldName != null) {
                                    if (tree.visitField(tsrgFieldName, tsrgFieldDesc)) {
                                        tree.visitDstName(MappedElementKind.FIELD, 0, tsrgFieldName)
                                        tree.visitElementContent(MappedElementKind.FIELD)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        tree.visitEnd()
    }
}