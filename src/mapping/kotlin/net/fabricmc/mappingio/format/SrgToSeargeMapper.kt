package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import xyz.wagyourtail.unimined.util.FinalizeOnRead

class SrgToSeargeMapper(next: MappingVisitor?, val srgNs: String, val seargeNs: String, val mojmapNs: String, mojmapSupplier: () -> MappingTreeView): ForwardingMappingVisitor(next) {
    var idx = -1
    val mojmap by lazy(mojmapSupplier)
    val mojmapNsId by lazy { mojmap.getNamespaceId(mojmapNs) }
    var currentClass: String = ""

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: List<String>) {
        val copy = dstNamespaces.toMutableList()
        idx = dstNamespaces.indexOf(srgNs)
        if (idx != -1) {
            copy[idx] = seargeNs
        }
        super.visitNamespaces(srcNamespace, copy)
    }

    override fun visitClass(srcName: String): Boolean {
        currentClass = srcName
        return super.visitClass(srcName)
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
        if (targetKind == MappedElementKind.CLASS) {
            if (namespace == idx) {
                val mojmapName = mojmap.getClass(currentClass)?.getName(mojmapNsId)
                if (mojmapName != null) {
                    super.visitDstName(targetKind, idx, mojmapName)
                    return
                }
            }
        }
        super.visitDstName(targetKind, namespace, name)
    }


}