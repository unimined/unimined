package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import xyz.wagyourtail.unimined.api.mappings.MappingNamespace

/**
 * The class responsible for patching minecraft for jar mods.
 * @since 0.2.3
 */
interface JarModPatcher: MinecraftPatcher {

    override val prodNamespace: MappingNamespace
        get() = MappingNamespace.OFFICIAL

    var deleteMetaInf: Boolean
}