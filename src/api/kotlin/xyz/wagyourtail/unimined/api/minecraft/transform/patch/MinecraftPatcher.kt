package xyz.wagyourtail.unimined.api.minecraft.transform.patch

/**
 * The class responsible for patching minecraft.
 * @see [FabricPatcher], [JarModPatcher], [ForgePatcher]
 * @since 0.2.3
 */
interface MinecraftPatcher {
    fun name(): String {
        return this::class.simpleName!!
    }

    /**
     * the namespace to use for the production jar.
     */
    val prodNamespace: String

    /**
     * the namespace to use for fallback on the production jar.
     */
    val prodFallbackNamespace: String
        get() = "official"

    /**
     * the namespace to use for the development jar.
     */
    var devNamespace: String

    /**
     * the namespace to use for fallback on the development jar.
     */
    var devFallbackNamespace: String

}