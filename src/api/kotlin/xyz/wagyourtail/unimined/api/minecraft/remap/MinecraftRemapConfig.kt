package xyz.wagyourtail.unimined.api.minecraft.remap

import net.fabricmc.tinyremapper.TinyRemapper
import org.jetbrains.annotations.ApiStatus

abstract class MinecraftRemapConfig {

    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    abstract var tinyRemapperConf: TinyRemapper.Builder.() -> Unit

    @ApiStatus.Experimental
    abstract fun config(remapperBuilder: TinyRemapper.Builder.() -> Unit)
}