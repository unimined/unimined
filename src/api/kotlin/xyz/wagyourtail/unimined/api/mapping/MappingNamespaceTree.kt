package xyz.wagyourtail.unimined.api.mapping

import org.jetbrains.annotations.ApiStatus

/**
 * @since 1.0.0
 */

open class MappingNamespaceTree {

    @ApiStatus.Internal
    data class Namespace(val name: String, val named: Boolean, private val tree: MappingNamespaceTree) {
        private val _targets  = mutableSetOf<Namespace>()

        fun addTarget(ns: Namespace) {
            tree.checkFrozen()
            _targets.add(ns)
            ns._targets.add(this)
        }

        val targets: Set<Namespace>
            get() {
                tree.freeze()
                return _targets
            }

        override fun toString(): String {
            return name.lowercase()
        }
    }

    private val namespaces = mutableMapOf<String, Namespace>()
    private val targetRemapFuns = mutableMapOf<Namespace, () -> Set<Namespace>>()
    private var frozen = false

    val OFFICIAL = addNamespace("official", { emptySet() }, false)

    private fun checkFrozen() {
        if (frozen) throw IllegalStateException("Cannot modify after freeze")
    }

    private var isFreezing = false

    open fun freeze() {
        if (frozen || isFreezing) return
        isFreezing = true
        targetRemapFuns.forEach { k, v ->
            for (t in v()) {
                k.addTarget(t)
            }
        }
        frozen = true
    }

    @ApiStatus.Internal
    fun getNamespaces(): Map<String, Namespace> {
        freeze()
        return namespaces
    }

    @ApiStatus.Internal
    fun addNamespace(name: String, targets: () -> Set<Namespace>, named: Boolean): Namespace {
        checkFrozen()
        if (name.lowercase() in namespaces) throw IllegalArgumentException("Namespace $name already exists")
        val namespace = Namespace(name, named, this)
        namespaces[name.lowercase()] = namespace
        targetRemapFuns[namespace] = targets
        return namespace
    }

    @ApiStatus.Experimental
    fun addTarget(namespace: String, canRemapTo: String) {
        checkFrozen()
        getNamespace(namespace.lowercase()).addTarget(getNamespace(canRemapTo.lowercase()))
    }

    @ApiStatus.Internal
    fun getRemapPath(fromNS: Namespace, fromFallbackNS: Namespace, toFallbackNS: Namespace, toNS: Namespace): List<Namespace> {
        freeze()
        // breadth first, as that should be the shortest route
        val queue = mutableListOf<List<Namespace>>()
        if (toNS != toFallbackNS && !toFallbackNS.targets.contains(toNS)) {
            throw IllegalArgumentException("Cannot remap from \"$toFallbackNS\" to \"$toNS\". $this")
        }
        if (fromNS != fromFallbackNS) {
            if (!fromNS.targets.contains(fromFallbackNS)) {
                throw IllegalArgumentException("Cannot remap from \"$fromNS\" to \"$fromFallbackNS\". $this")
            }
            queue.add(listOf(fromFallbackNS))
        } else {
            queue.add(listOf(fromNS))
        }
//        if (fromFallbackNS == toFallbackNS) {
//            return if (toNS == toFallbackNS) {
//                queue.first().subList(1, queue.first().size)
//            } else {
//                queue.first().subList(1, queue.first().size) + toNS
//            }
//        }
//        if (fromNS == toFallbackNS) {
//            return if (toNS == toFallbackNS) {
//                emptyList()
//            } else {
//                listOf(toNS)
//            }
//        }
        while (queue.isNotEmpty()) {
            val path = queue.removeAt(0)
            val last = path.last()
            if (last == toFallbackNS) {
                var retPath = if (toNS == toFallbackNS) {
                    path
                } else {
                    path + toNS
                }
                if (fromNS != fromFallbackNS) {
                    retPath = listOf(fromNS) + retPath
                }
                if (retPath.size > 2) {
                    // if it remaps back like
                    // searge -> official -> searge -> mcp
                    // remove the first 1, as this is a detected extra remap step
                    if (retPath[0] == retPath[2]) {
                        return retPath.subList(3, retPath.size)
                    }
                }
                if (retPath.size > 2) {
                    // if it remaps back like
                    // official -> searge -> mcp -> searge
                    // remove the last 2, as this is a detected backwards remap
                    if (retPath[retPath.size - 1] == retPath[retPath.size - 3]) {
                        return retPath.subList(1, retPath.size - 2)
                    }
                }
                return retPath.subList(1, retPath.size)
            }
            for (target in last.targets) {
                if (target !in path) {
                    queue.add(path + target)
                }
            }
        }
        throw IllegalArgumentException("Cannot remap from \"$fromNS\" to \"$toNS\". $this")
    }

    @ApiStatus.Experimental
    fun getNamespace(name: String): Namespace {
        return namespaces[name.lowercase()] ?: throw IllegalArgumentException("Namespace $name does not exist")
    }

    override fun toString(): String {
        var s = "MappingNamespaceTree.MappingNamespace: {\n"
        for (namespace in namespaces.values) {
            s += "  $namespace -> ${namespace.targets}\n"
        }
        s += "}"
        return s
    }
}