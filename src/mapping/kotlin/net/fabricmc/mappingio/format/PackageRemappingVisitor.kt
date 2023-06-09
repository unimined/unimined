package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.tree.MappingTree
import xyz.wagyourtail.unimined.util.globToRegex

class PackageRemappingVisitor(next: MappingVisitor?, val namespaces: Set<String>, matcherGlobToPackage: List<Pair<String, String>>) : ForwardingMappingVisitor(next) {

    val matcherRegexToPackage = matcherGlobToPackage.map { it.first.globToRegex() to it.second }.reversed()

    lateinit var namespaceIds: Set<Int>

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        super.visitNamespaces(srcNamespace, dstNamespaces)
        val ns = mutableSetOf<Int>()
        if (namespaces.contains(srcNamespace)) {
            ns.add(MappingTree.SRC_NAMESPACE_ID)
        }
        for ((i, dstNamespace) in dstNamespaces.withIndex()) {
            if (namespaces.contains(dstNamespace)) {
                ns.add(i)
            }
        }
        namespaceIds = ns
    }

    fun remapClassName(name: String): String {
        val remappedPackage = matcherRegexToPackage.firstOrNull { it.first.matches(name) }?.second
        if (remappedPackage != null) {
            val cName = name.substringAfterLast('/')
            return if (remappedPackage.endsWith('/')) {
                remappedPackage + cName
            } else {
                "$remappedPackage/$cName"
            }
        }
        return name
    }
    override fun visitClass(srcName: String): Boolean {
        if (namespaceIds.contains(MappingTree.SRC_NAMESPACE_ID)) {
            return super.visitClass(remapClassName(srcName))
        }
        return super.visitClass(srcName)
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String) {
        if (namespaceIds.contains(namespace) && targetKind == MappedElementKind.CLASS) {
            super.visitDstName(targetKind, namespace, remapClassName(name))
        } else {
            super.visitDstName(targetKind, namespace, name)
        }
    }

    override fun visitMethod(srcName: String, srcDesc: String): Boolean {
        if (namespaceIds.contains(MappingTree.SRC_NAMESPACE_ID)) {
            return super.visitMethod(srcName, srcDesc.replace(Regex("L(.+);")) { remapClassName(it.groupValues[1]) })
        }
        return super.visitMethod(srcName, srcDesc)
    }

    override fun visitField(srcName: String, srcDesc: String?): Boolean {
        if (namespaceIds.contains(MappingTree.SRC_NAMESPACE_ID)) {
            return super.visitField(srcName, srcDesc?.replace(Regex("L(.+);")) { remapClassName(it.groupValues[1]) })
        }
        return super.visitField(srcName, srcDesc)
    }

    override fun visitDstDesc(targetKind: MappedElementKind, namespace: Int, desc: String?) {
        if (namespaceIds.contains(namespace)) {
            super.visitDstDesc(targetKind, namespace, desc?.replace(Regex("L(.+);")) { remapClassName(it.groupValues[1]) })
        } else {
            super.visitDstDesc(targetKind, namespace, desc)
        }
    }

}