package xyz.wagyourtail.unimined.api.mapping

import org.jetbrains.annotations.ApiStatus

/**
 * @since 1.0.0
 */

class MappingNamespace

@ApiStatus.Internal
constructor(val namespace: String, val type: Type,
    @set:ApiStatus.Internal
    var canRemapTo: MappingNamespace.(MappingNamespace) -> Pair<Boolean, Boolean> = {
        (
            if (type.ordinal != 2) {
                Type.values()[type.ordinal + 1].let { byType[it] }!!
            } else { emptyList() } +
            if (type.ordinal != 0) {
                Type.values()[type.ordinal - 1].let { byType[it] }!!
            } else { emptyList() }
        ).contains(it) to shouldReverse(it)

    }
) {
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
        return target.type.ordinal > this.type.ordinal
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
            val canRemapTo = from.canRemapTo
            val targets = available.associateWith { from.canRemapTo(it) }.filterValues { it.first }.mapValues { it.value.second }
            for (target in targets) {
                if (current.any { it.second == target.key }) continue
                if (target.key == to) {
                    current.add(target.value to target.key)
                    return current
                }
                val newCurrent = current.toMutableList()
                newCurrent.add(target.value to target.key)
                val result = calculateShortestRemapPathInternal(target.key, to, available, newCurrent)
                if (result != null) results.add(result)
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
                ?: throw IllegalArgumentException("Invalid target: $to, available: $available")
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