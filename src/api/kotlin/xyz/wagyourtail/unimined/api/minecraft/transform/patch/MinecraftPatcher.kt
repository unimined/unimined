package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace

/**
 * The class responsible for patching minecraft.
 * @see [FabricLikePatcher], [JarModPatcher], [ForgePatcher]
 * @since 0.2.3
 */
interface MinecraftPatcher {
    fun name(): String {
        return this::class.simpleName!!
    }

    /**
     * the namespace to use for the production jar.
     */
    @get:ApiStatus.Internal
    val prodNamespace: MappingNamespace

    /**
     * the namespace to use for the development jar.
     */
    @set:ApiStatus.Internal
    var devNamespace: MappingNamespace

    /**
     * the namespace to use for fallback on the development jar.
     */
    @set:ApiStatus.Internal
    var devFallbackNamespace: MappingNamespace

    fun setDevNamespace(namespace: String) {
        devNamespace = MappingNamespace.getNamespace(namespace)
    }

    fun setDevFallbackNamespace(namespace: String) {
        devFallbackNamespace = MappingNamespace.getNamespace(namespace)
    }

}