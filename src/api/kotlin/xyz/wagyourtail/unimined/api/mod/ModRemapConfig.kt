package xyz.wagyourtail.unimined.api.mod

import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.artifacts.Configuration
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable

abstract class ModRemapConfig(val configurations: Set<Configuration>) {

    abstract var prodNamespace: MappingNamespace
    abstract var prodFallbackNamespace: MappingNamespace

    @set:ApiStatus.Experimental
    abstract var remapAtToLegacy: Boolean

    @set:ApiStatus.Internal
    var mixinRemap: MixinRemap by FinalizeOnRead(LazyMutable { MixinRemap.NONE })

    fun mixinRemap(remap: String) {
        mixinRemap = MixinRemap.valueOf(remap.uppercase())
    }

    @ApiStatus.Experimental
    abstract fun remapper(remapperBuilder: TinyRemapper.Builder.() -> Unit)

    enum class MixinRemap {
        NONE,
        TINY_HARD,
        TINY_HARDSOFT,
        UNIMINED
    }
}