package xyz.wagyourtail.unimined.api.mappings

import kotlin.math.abs

class MappingNamespace(val namespace: String, val type: Type) {
    enum class Type(val id: String) {
        NAMED("named"),
        INT("intermediary"),
        OBF("official")
        ;

        companion object {

            val byId = values().associateBy { it.id }

            fun fromId(id: String): Type {
                return byId[id] ?: throw IllegalArgumentException("Invalid type: $id, available: ${byId.keys}")
            }

        }
    }

    fun shouldReverse(target: MappingNamespace): Boolean {
        val type = target.type
        if (abs(type.ordinal - this.type.ordinal) == 1) {
            return type.ordinal > this.type.ordinal
        }
        throw IllegalArgumentException("Invalid target $target for $this")
    }

    companion object {
        private val values = mutableListOf<MappingNamespace>()
        private val byName = mutableMapOf<String, MappingNamespace>()
        private val byType = mutableMapOf<Type, MutableSet<MappingNamespace>>()

        val OFFICIAL = MappingNamespace("official", Type.OBF)
        val INTERMEDIARY = MappingNamespace("intermediary", Type.INT)
        val SEARGE = MappingNamespace("searge", Type.INT)
        val HASHED = MappingNamespace("hashed", Type.INT)
        val MOJMAP = MappingNamespace("mojmap", Type.NAMED)
        val MCP = MappingNamespace("mcp", Type.NAMED)
        val YARN = MappingNamespace("yarn", Type.NAMED)
        val QUILT = MappingNamespace("quilt", Type.NAMED)

        private fun addNS(ns: MappingNamespace) {
            values.add(ns)
            byName[ns.namespace] = ns
            byType.computeIfAbsent(ns.type) { mutableSetOf() }.add(ns)
        }

        fun values(): List<MappingNamespace> {
            return values
        }

        fun getNamespace(namespace: String): MappingNamespace {
            return byName[namespace] ?: throw IllegalArgumentException("Invalid namespace: $namespace, available: ${byName.keys}")
        }

        fun calculateShortestRemapPathInternal(
            from: MappingNamespace,
            to: MappingNamespace,
            available: Set<MappingNamespace>,
            current: MutableList<Pair<Boolean, MappingNamespace>>
        ): MutableList<Pair<Boolean, MappingNamespace>>? {
            val results = mutableListOf<MutableList<Pair<Boolean, MappingNamespace>>>()

            if (from.type.ordinal != 2) {
                for (target in Type.values()[from.type.ordinal + 1].let { byType[it] }!!) {
                    if (!available.contains(target)) continue
                    if (current.any { it.second == target }) continue
                    if (target == to) {
                        current.add(true to target)
                        return current
                    }
                    val newCurrent = current.toMutableList()
                    newCurrent.add(true to target)
                    val result = calculateShortestRemapPathInternal(target, to, available, newCurrent)
                    if (result != null) results.add(result)
                }
            }

            if (from.type.ordinal != 0) {
                for (target in Type.values()[from.type.ordinal - 1].let { byType[it] }!!) {
                    if (!available.contains(target)) continue
                    if (current.any { it.second == target }) continue
                    if (target == to) {
                        current.add(false to target)
                        return current
                    }
                    val newCurrent = current.toMutableList()
                    newCurrent.add(false to target)
                    val result = calculateShortestRemapPathInternal(target, to, available, newCurrent)
                    if (result != null) results.add(result)
                }
            }
            return results.minByOrNull { it.size }
        }

        fun calculateShortestRemapPathWithFallbacks(
            from: MappingNamespace,
            fromFallback: MappingNamespace,
            toFallback: MappingNamespace,
            to: MappingNamespace,
            available: Set<MappingNamespace>
        ): List<Pair<Boolean, MappingNamespace>> {
            val path = if (fromFallback == toFallback) mutableListOf() else calculateShortestRemapPathInternal(
                fromFallback,
                toFallback,
                available,
                mutableListOf()
            )
                ?: throw IllegalArgumentException("Invalid target: $to")
            if (from != fromFallback) {
                path.add(0, from.shouldReverse(fromFallback) to fromFallback)
                if (path.size > 1 && from == path[1].second) {
                    path.removeAll(listOf(path[0], path[1]))
                }
            }
            if (toFallback != to)
                path.add(toFallback.shouldReverse(to) to to)
            return path
        }

        fun findByType(type: Type, available: Set<MappingNamespace>): MappingNamespace {
            return byType[type]?.firstOrNull { it in available }
                ?: throw IllegalArgumentException("No valid namespaces for type $type in $available")
        }

    }

    init {
        addNS(this)
    }

    override fun toString(): String {
        return namespace.uppercase()
    }
}