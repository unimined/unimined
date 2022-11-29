package xyz.wagyourtail.unimined.api.minecraft.transform.patch

/**
 * The class responsible for patching minecraft for jar mods.
 * @since 0.2.3
 */
interface JarModPatcher : MinecraftPatcher {

    override val prodNamespace: String
        get() = "official"

}