package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView

class ClassNameReplacer(next: MappingVisitor?, val targetNsName: String, val classNsName: String, val tree: MappingTreeView): ForwardingMappingVisitor(next) {
    var idx = -1
    var srcNsId = -1
    val classNsId = tree.getNamespaceId(classNsName)
    var currentClass: String = ""

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: List<String>) {
        idx = dstNamespaces.indexOf(targetNsName)
        srcNsId = tree.getNamespaceId(srcNamespace)
        super.visitNamespaces(srcNamespace, dstNamespaces)
    }

    override fun visitClass(srcName: String): Boolean {
        currentClass = srcName
        val visit = super.visitClass(srcName)
        if (visit) {
            val mojmapName = tree.getClass(currentClass, srcNsId)?.getName(classNsId)
            if (mojmapName != null) {
                super.visitDstName(MappedElementKind.CLASS, idx, mojmapName)
            }
        }
        return visit
    }

    override fun visitElementContent(targetKind: MappedElementKind): Boolean {
        return super.visitElementContent(targetKind)
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
        if (targetKind == MappedElementKind.CLASS && namespace == idx) {
            return
        }
        super.visitDstName(targetKind, namespace, name)
    }


}