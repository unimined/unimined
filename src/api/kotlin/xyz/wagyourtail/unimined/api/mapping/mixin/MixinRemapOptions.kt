package xyz.wagyourtail.unimined.api.mapping.mixin

import org.jetbrains.annotations.ApiStatus

/**
 * @since 1.1.0
 */
interface MixinRemapOptions {

    fun enableMixinExtra()

    fun enableBaseMixin()

    fun enableJarModAgent()

    @ApiStatus.Experimental
    fun reset()

    @ApiStatus.Experimental
    fun resetMetadataReader()

    @ApiStatus.Experimental
    fun resetHardRemapper()

    @ApiStatus.Experimental
    fun resetRefmapBuilder()
    fun off()

    fun disableRefmap()
    fun disableRefmap(keys: List<String> = listOf("BaseMixin", "JarModAgent"))
}