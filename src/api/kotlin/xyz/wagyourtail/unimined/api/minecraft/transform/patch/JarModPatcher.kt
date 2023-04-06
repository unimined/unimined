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

    /**
     * @since 0.4.10
     * enable for fixes that allow class overrides and transforms to work.
     */
    var projectIsJarMod: Boolean

    /**
     * @since 0.4.10
     * add a transform file for ClassTransform with the java agent.
     */
    var transforms: String?
}