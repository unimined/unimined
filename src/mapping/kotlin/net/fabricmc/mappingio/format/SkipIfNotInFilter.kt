package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView
import kotlin.properties.Delegates

class SkipIfNotInFilter(next: MappingVisitor, val mappings: MappingTreeView, val namespace: String) : ForwardingMappingVisitor(next) {

    var filterNsId by Delegates.notNull<Int>()
    var namespaceId by Delegates.notNull<Int>()
    lateinit var clazz: ClassMappingView
    var skip: MappedElementKind? = null

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        filterNsId = mappings.getNamespaceId(namespace)

        if (filterNsId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $namespace does not exist")
        }

        namespaceId = mappings.getNamespaceId(srcNamespace)

        if (namespaceId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalStateException("Namespace $srcNamespace does not exist")
        }

        if (dstNamespaces.contains(namespace)) {
            throw IllegalStateException("Namespace $namespace is in dst and can't be used for filtering")
        }

        super.visitNamespaces(srcNamespace, dstNamespaces)
    }

    override fun visitClass(srcName: String): Boolean {
        clazz = mappings.getClass(srcName, namespaceId)
        skip = if (clazz.getDstName(filterNsId) == null) {
            MappedElementKind.CLASS
        } else {
            null
        }
        return super.visitClass(srcName)
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String) {
        if (targetKind == skip) {
            return
        }
        super.visitDstName(targetKind, namespace, name)
    }

    override fun visitMethod(srcName: String, srcDesc: String): Boolean {
        val method = clazz.getMethod(srcName, srcDesc, namespaceId)
        skip = if (method?.getDstName(filterNsId) == null) {
            MappedElementKind.METHOD
        } else {
            null
        }
        return super.visitMethod(srcName, srcDesc)
    }

    override fun visitField(srcName: String, srcDesc: String?): Boolean {
        val field = clazz.getField(srcName, srcDesc, namespaceId)
        skip = if (field?.getDstName(filterNsId) == null) {
            MappedElementKind.FIELD
        } else {
            null
        }
        return super.visitField(srcName, srcDesc)
    }
}