package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import java.io.File

/**
 * The class responsible for patching minecraft for fabric.
 * @since 0.2.3
 */
interface FabricPatcher : MinecraftPatcher {

    override val prodNamespace: String
        get() = "intermediary"

    override val prodFallbackNamespace: String
        get() = "intermediary"

    /**
     * location of access widener file to apply to the minecraft jar.
     */
    var accessWidener: File?

    /**
     * set the access widener file to apply to the minecraft jar.
     */
    fun setAccessWidener(file: String) {
        accessWidener = File(file)
    }
}