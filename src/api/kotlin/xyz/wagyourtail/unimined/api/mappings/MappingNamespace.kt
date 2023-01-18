package xyz.wagyourtail.unimined.api.mappings

enum class MappingNamespace(val namespace: String, val validTargets: Set<String>, val type: Type) {
    OFFICIAL("official", setOf("intermediary", "searge", "hashed", "mojmap"), Type.OBF),
    INTERMEDIARY("intermediary", setOf("yarn", "mojmap", "quilt"), Type.INT),
    SEARGE("searge", setOf("mcp", "mojmap"), Type.INT),
    HASHED("hashed", setOf("quilt", "mojmap"), Type.INT),
    MOJMAP("mojmap", setOf(), Type.NAMED),
    MCP("mcp", setOf(), Type.NAMED),
    YARN("yarn", setOf(), Type.NAMED),
    QUILT("quilt", setOf(), Type.NAMED),
    ;

    enum class Type {
        NAMED, INT, OBF
    }

    fun shouldReverse(target: MappingNamespace): Boolean {
        if (this == target) throw IllegalArgumentException("Same mappings is not a valid target.")
        if (target.namespace in validTargets) return false
        if (namespace in target.validTargets) return true
        throw IllegalArgumentException("Invalid target $target for $this")
    }

    companion object {
        val byName: Map<String, MappingNamespace> = values().associateBy { it.namespace }
        val targetsFor: Map<MappingNamespace, Set<MappingNamespace>> = values().associateWith { ns ->
            values().filter { ns.validTargets.contains(it.namespace) }.toSet()
        }
        val reverseTargetsFor: Map<MappingNamespace, Set<MappingNamespace>> = values().associateWith { ns ->
            values().filter { ns.namespace in it.validTargets }.toSet()
        }
        val byType: Map<Type, Set<MappingNamespace>> = values().groupBy { it.type }.mapValues { it.value.toSet() }

        fun getNamespace(namespace: String): MappingNamespace {
            return byName[namespace] ?: throw IllegalArgumentException("Invalid namespace: $namespace")
        }

        fun calculateShortestRemapPathInternal(from: MappingNamespace, to: MappingNamespace, available: Set<MappingNamespace>, current: MutableList<Pair<Boolean, MappingNamespace>>): MutableList<Pair<Boolean, MappingNamespace>>? {
            val results = mutableListOf<MutableList<Pair<Boolean, MappingNamespace>>>()

            for (target in reverseTargetsFor[from]!!) {
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

            for (target in targetsFor[from]!!) {
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
            return results.minByOrNull { it.size }
        }

        fun calculateShortestRemapPathWithFallbacks(from: MappingNamespace, fromFallback: MappingNamespace, toFallback: MappingNamespace, to: MappingNamespace, available: Set<MappingNamespace>): List<Pair<Boolean, MappingNamespace>> {
            val path = if (fromFallback == toFallback) mutableListOf() else calculateShortestRemapPathInternal(fromFallback, toFallback, available, mutableListOf())
                ?: throw IllegalArgumentException("Invalid target: $to")
            if (from != fromFallback)
                path.add(0, from.shouldReverse(fromFallback) to fromFallback)
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
            MappingNamespace.getNamespace("intermediary"),
            MappingNamespace.getNamespace("intermediary"),
            MappingNamespace.getNamespace("intermediary"),
            MappingNamespace.getNamespace("yarn"),
            setOf(MappingNamespace.INTERMEDIARY, MappingNamespace.YARN, MappingNamespace.OFFICIAL)
        )
    )
}