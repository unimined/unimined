package xyz.wagyourtail.unimined.api.minecraft.patch

import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.patch.MinecraftPatcher

/**
 * The class responsible for patching minecraft for jar mods.
 * @since 0.2.3
 */
interface JarModPatcher: MinecraftPatcher {

    override val prodNamespace: MappingNamespace
        get() = MappingNamespace.OFFICIAL

    var deleteMetaInf: Boolean
}