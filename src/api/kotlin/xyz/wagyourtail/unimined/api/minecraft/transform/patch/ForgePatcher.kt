package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import java.io.File

/**
 * The class responsible for patching minecraft for forge.
 * @since 0.2.3
 */
interface ForgePatcher : JarModPatcher, AccessTransformablePatcher {

    /**
     * location of access transformer file to apply to the minecraft jar.
     */
    var accessTransformer: File?

    /**
     * set mcp version to use for remapping.
     */
    var mcpVersion: String?

    /**
     * set mcp channel to use for remapping.
     */
    var mcpChannel: String?

    /**
     * add mixin configs for launch
     */
    var mixinConfig: List<String>

    /**
     * add mixin configs for launch
     */
    var mixinConfigs: List<String>
        get() = mixinConfig
        set(value) {
            mixinConfig = value
        }

    /**
     * set the access transformer file to apply to the minecraft jar.
     */
    fun setAccessTransformer(file: String) {
        accessTransformer = File(file)
    }

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: String): File

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: String, output: String): File

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: File): File

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: File, output: File): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: String): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: String, output: String): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: File): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: File, output: File): File
}