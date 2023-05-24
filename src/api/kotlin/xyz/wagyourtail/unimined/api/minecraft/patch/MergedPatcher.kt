package xyz.wagyourtail.unimined.api.minecraft.patch

import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.PatchProviders

/**
 * @since 0.4.10
 * @see PatchProviders
 */
interface MergedPatcher: MinecraftPatcher, PatchProviders {
    override var prodNamespace: MappingNamespace

    fun setProdNamespace(namespace: String) {
        prodNamespace = MappingNamespace.getNamespace(namespace)
    }
}