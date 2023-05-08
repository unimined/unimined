package xyz.wagyourtail.unimined.api.mapping

import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.util.LazyMutable

/**
 * @since 1.0.0
 */
abstract class MappingDepConfig<T : Dependency>(val dep: T, val mappingsConfig: MappingsConfig) : Dependency {

    /**
     * Maps namespace names for file formats that support naming namespaces.
     * If you use the name of a detected namespace for a file format that doesn't, it will still
     * work...
     *
     * This can be used for things such as changing which namespace is used for official
     * on pre-1.2.5, For example, with retroMCP they use client/server for the official mappings
     * and so you want to get those recognized as official mappings instead of the default
     * for unimined to use.
     */
    val mapNamespace = mutableMapOf<String, MappingNamespace>()

    /**
     * override the side used for mappings with side information
     * (like mcp)
     */
    val side: EnvType by LazyMutable {
        mappingsConfig.side
    }

}