package xyz.wagyourtail.unimined.api.minecraft.patch

import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.PatchProviders

/**
 * @since 0.4.10
 * @see PatchProviders
 */
interface MergedPatcher: MinecraftPatcher, PatchProviders {
    override var prodNamespace: MappingNamespaceTree.Namespace

    @Deprecated("use prodNamespace instead", ReplaceWith("prodNamespace"))
    fun setProdNamespace(namespace: String)

    fun prodNamespace(namespace: String)
}