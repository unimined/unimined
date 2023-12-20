package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingFlag
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import net.fabricmc.mappingio.tree.MappingTreeView

class MemberNameReplacer(next: MappingVisitor?, val targetNsName: String, memberNsName: String, val tree: MappingTreeView, val types: Set<MappedElementKind>): ForwardingMappingVisitor(next) {
    var idx = -1
    var srcNsId = -1
    val memberNsId = tree.getNamespaceId(memberNsName)
    var currentClass: String = ""


    override fun getFlags(): MutableSet<MappingFlag> {
        val flags = super.getFlags()
        if (flags.contains(MappingFlag.NEEDS_UNIQUENESS)) {
            throw UnsupportedOperationException("MemberNameReplacer does not support mappings that require uniqueness")
        }
        return flags
    }

    override fun visitNamespaces(srcNamespace: String, dstNamespaces: List<String>) {
        idx = dstNamespaces.indexOf(targetNsName)
        srcNsId = tree.getNamespaceId(srcNamespace)
        super.visitNamespaces(srcNamespace, dstNamespaces)
    }

    override fun visitClass(srcName: String): Boolean {
        currentClass = srcName
        return super.visitClass(srcName)
    }

    override fun visitMethod(srcName: String, srcDesc: String): Boolean {
        return super.visitMethod(srcName, srcDesc)
    }

    override fun visitField(srcName: String, srcDesc: String?): Boolean {
        return super.visitField(srcName, srcDesc)
    }

    override fun visitElementContent(targetKind: MappedElementKind): Boolean {
        val value = super.visitElementContent(targetKind)
        if (value) {
            if (targetKind == MappedElementKind.CLASS) {
                val clazz = tree.getClass(currentClass) ?: return true
                if (types.contains(MappedElementKind.METHOD)) {
                    for (method in clazz.methods) {
                        val mojmapName = method.getName(memberNsId)
                        if (mojmapName != null) {
                            if (super.visitMethod(method.getName(srcNsId), method.getDesc(srcNsId))) {
                                super.visitDstName(MappedElementKind.METHOD, idx, mojmapName)
                                if (types.contains(MappedElementKind.METHOD_ARG) || types.contains(MappedElementKind.METHOD_VAR)) {
                                    if (super.visitElementContent(MappedElementKind.METHOD)) {
                                        if (types.contains(MappedElementKind.METHOD_ARG)) {
                                            for (arg in method.args) {
                                                val mojmapName = arg.getName(memberNsId)
                                                if (mojmapName != null) {
                                                    if (super.visitMethodArg(
                                                            arg.argPosition,
                                                            arg.lvIndex,
                                                            arg.getName(srcNsId)
                                                        )
                                                    ) {
                                                        super.visitDstName(
                                                            MappedElementKind.METHOD_ARG,
                                                            idx,
                                                            mojmapName
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (types.contains(MappedElementKind.METHOD_VAR)) {
                                            for (local in method.vars) {
                                                val mojmapName = local.getName(memberNsId)
                                                if (mojmapName != null) {
                                                    if (super.visitMethodVar(
                                                            local.lvIndex,
                                                            local.lvtRowIndex,
                                                            local.startOpIdx,
                                                            local.getName(srcNsId)
                                                        )
                                                    ) {
                                                        super.visitDstName(
                                                            MappedElementKind.METHOD_VAR,
                                                            idx,
                                                            mojmapName
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (types.contains(MappedElementKind.FIELD)) {
                    for (field in clazz.fields) {
                        val mojmapName = field.getName(memberNsId)
                        if (mojmapName != null) {
                            if (super.visitField(field.getName(srcNsId), null)) {
                                super.visitDstName(MappedElementKind.FIELD, idx, mojmapName)
                            }
                        }
                    }
                }
            }
        }
        return value
    }

    override fun visitDstName(targetKind: MappedElementKind, namespace: Int, name: String?) {
        if (targetKind != MappedElementKind.CLASS && namespace == idx) {
            return
        }
        super.visitDstName(targetKind, namespace, name)
    }


}