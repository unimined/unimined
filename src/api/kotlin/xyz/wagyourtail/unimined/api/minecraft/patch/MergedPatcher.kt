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
    override var prodNamespace: Namespace

    /**
     * @since 1.0.2
     * whether librarys need to match any, or all, of the rules of the patchers
     */
    @set:ApiStatus.Internal
    var libraryFilter: LibraryFilter

    @Deprecated("use prodNamespace instead", ReplaceWith("prodNamespace"))
    fun setProdNamespace(namespace: String)

    fun prodNamespace(namespace: String)


    /**
     * @since 1.0.2
     */
    fun libraryFilter(filter: String) {
        libraryFilter = LibraryFilter.valueOf(filter.uppercase())
    }

    /**
     * set which vanilla libraries to include in minecraftLibraries
     * @since 1.0.2
     */
    @ApiStatus.Experimental
    fun customLibraryFilter(filter: (String) -> Boolean)

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
        filter: Closure<Boolean>
    ) {
        customLibraryFilter(filter::call)
    }

    enum class LibraryFilter {
        ANY,
        ALL,
        CUSTOM
    }
}