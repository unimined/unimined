package xyz.wagyourtail.unimined.api.mappings

import kotlin.math.abs

enum class MappingNamespace(val namespace: String, val type: Type) {
    OFFICIAL("official", Type.OBF),
    INTERMEDIARY("intermediary", Type.INT),
    SEARGE("searge", Type.INT),
    HASHED("hashed", Type.INT),
    MOJMAP("mojmap", Type.NAMED),
    MCP("mcp", Type.NAMED),
    YARN("yarn", Type.NAMED),
    QUILT("quilt", Type.NAMED),
    ;

    enum class Type(val id: String) {
        NAMED("named"),
        INT("intermediary"),
        OBF("official")
    }

    fun shouldReverse(target: MappingNamespace): Boolean {
        val type = target.type
        if (abs(type.ordinal - this.type.ordinal) == 1) {
            return type.ordinal > this.type.ordinal
        }
        throw IllegalArgumentException("Invalid target $target for $this")
    }

    companion object {
        val byName: Map<String, MappingNamespace> = values().associateBy { it.namespace }
        val byType: Map<Type, Set<MappingNamespace>> = values().groupBy { it.type }.mapValues { it.value.toSet() }

        fun getNamespace(namespace: String): MappingNamespace {
            return byName[namespace] ?: throw IllegalArgumentException("Invalid namespace: $namespace")
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
}

fun main(args: Array<String>) {
    println(
        MappingNamespace.calculateShortestRemapPathWithFallbacks(
            MappingNamespace.getNamespace("searge"),
            MappingNamespace.getNamespace("official"),
            MappingNamespace.getNamespace("searge"),
            MappingNamespace.getNamespace("mojmap"),
            setOf(
                MappingNamespace.MOJMAP,
                MappingNamespace.SEARGE,
                MappingNamespace.OFFICIAL
            )
        )
    )
}