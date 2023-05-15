package xyz.wagyourtail.unimined.api.mod

import net.fabricmc.tinyremapper.TinyRemapper
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace

abstract class ModRemapSettings {

    abstract val prodNamespace: MappingNamespace

    abstract val devNamespace: MappingNamespace
    abstract val devFallbackNamespace: MappingNamespace

    @ApiStatus.Experimental
    abstract fun remapper(remapperBuilder: TinyRemapper.Builder.() -> Unit)
}