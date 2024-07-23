package xyz.wagyourtail.unimined.api.minecraft.remap

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.mapping.Namespace

abstract class MinecraftRemapConfig {

    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    abstract var tinyRemapperConf: TinyRemapper.Builder.() -> Unit

    @ApiStatus.Experimental
    abstract fun config(remapperBuilder: TinyRemapper.Builder.() -> Unit)

    @ApiStatus.Experimental
    fun config(
        @DelegatesTo(
            value = TinyRemapper.Builder::class,
            strategy = Closure.DELEGATE_FIRST
        )
        remapperBuilder: Closure<*>
    ) {
        config {
            remapperBuilder.delegate = this
            remapperBuilder.resolveStrategy = Closure.DELEGATE_FIRST
            remapperBuilder.call()
        }
    }

    abstract var replaceJSRWithJetbrains: Boolean
    @ApiStatus.Experimental
    abstract fun addResourceRemapper(remapper: (Namespace, Namespace) -> OutputConsumerPath.ResourceRemapper)

    @ApiStatus.Experimental
    abstract fun addExtension(extension: () -> TinyRemapper.Extension)
}