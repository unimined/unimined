package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView
import kotlin.properties.Delegates

class NoNewSrcVisitor(next: MappingVisitor, val existingMappings: MappingTreeView) : ForwardingMappingVisitor(next) {

    var srcNamespace by Delegates.notNull<Int>()
    var currentClass: ClassMappingView? = null

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: MutableList<String>) {
        this.srcNamespace = existingMappings.getNamespaceId(srcNamespace)
        if (this.srcNamespace == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalArgumentException("Source namespace $srcNamespace does not exist in existing mappings")
        }
        super.visitNamespaces(srcNamespace, dstNamespaces)
    }

    override fun visitClass(srcName: String): Boolean {
        currentClass = existingMappings.getClass(srcName, srcNamespace)
        if (currentClass == null) {
            return false
        }
        return super.visitClass(srcName)
    }

    override fun visitMethod(srcName: String, srcDesc: String?): Boolean {
        if (currentClass!!.getMethod(srcName, srcDesc, srcNamespace) == null) {
            return false
        }
        return super.visitMethod(srcName, srcDesc)
    }

    override fun visitField(srcName: String, srcDesc: String?): Boolean {
        if (currentClass!!.getField(srcName, srcDesc, srcNamespace) == null) {
            return false
        }
        return super.visitField(srcName, srcDesc)
    }
}