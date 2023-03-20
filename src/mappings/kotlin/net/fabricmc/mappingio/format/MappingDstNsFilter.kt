package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor

class MappingDstNsFilter(next: MappingVisitor?, val namespaces: List<String>): ForwardingMappingVisitor(next) {
    private lateinit var nsMap: Map<Int, Int>

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        nsMap = dstNamespaces.mapIndexedNotNull { index, s -> if (namespaces.contains(s)) index to namespaces.indexOf(s) else null }
            .toMap()
        super.visitNamespaces(srcNamespace, namespaces.filter { dstNamespaces.contains(it) })
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
        if (nsMap.containsKey(namespace)) {
            super.visitDstName(targetKind, nsMap[namespace]!!, name)
        }
    }

    override fun visitDstDesc(targetKind: MappedElementKind?, namespace: Int, desc: String?) {
        if (nsMap.containsKey(namespace)) {
            super.visitDstDesc(targetKind, nsMap[namespace]!!, desc)
        }
    }


}