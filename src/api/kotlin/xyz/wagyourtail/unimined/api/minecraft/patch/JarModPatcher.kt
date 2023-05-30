package xyz.wagyourtail.unimined.api.minecraft.patch

import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree

/**
 * The class responsible for patching minecraft for jar mods.
 * @since 0.2.3
 */
interface JarModPatcher: MinecraftPatcher {

    override val prodNamespace: MappingNamespaceTree.Namespace

    @set:ApiStatus.Experimental
    var deleteMetaInf: Boolean
}