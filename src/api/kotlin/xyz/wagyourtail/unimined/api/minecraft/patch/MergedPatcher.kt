package xyz.wagyourtail.unimined.api.minecraft.patch

import groovy.lang.Closure
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.PatchProviders
import xyz.wagyourtail.unimined.mapping.Namespace

/**
 * @since 0.4.10
 * @see PatchProviders
 */
interface MergedPatcher: MinecraftPatcher, PatchProviders {

    @Deprecated("use prodNamespace instead", ReplaceWith("prodNamespace"))
    fun setProdNamespace(namespace: String)

    override fun prodNamespace(namespace: String)

    /**
     * set which vanilla libraries to include in minecraftLibraries
     * @since 1.0.2
     */
    @ApiStatus.Experimental
    fun customLibraryFilter(filter: (String) -> String?)

    /**
     * @since 1.0.2
     */
    @ApiStatus.Experimental
    fun customLibraryFilter(
        @ClosureParams(
            value = SimpleType::class,
            options = [
                "java.lang.String"
            ]
        )
        filter: Closure<String?>
    ) {
        customLibraryFilter(filter::call)
    }

}