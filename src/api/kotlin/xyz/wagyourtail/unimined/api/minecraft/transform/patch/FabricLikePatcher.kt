package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import java.io.File

/**
 * The class responsible for patching minecraft for fabric.
 * @since 0.2.3
 */
interface FabricLikePatcher : MinecraftPatcher {

    override val prodNamespace: MappingNamespace
        get() = MappingNamespace.INTERMEDIARY

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