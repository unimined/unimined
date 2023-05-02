package xyz.wagyourtail.unimined.api.minecraft.transform.remap

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.tinyremapper.TinyRemapper
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher

/**
 * The class responsible for remapping minecraft.
 * @since 0.2.3
 */
abstract class MinecraftRemapper {

    /**
     * pass a closure to configure the remapper.
     * @since 0.2.3
     */
    @set:ApiStatus.Experimental
    var tinyRemapperConf: (TinyRemapper.Builder) -> Unit = {}

    /**
     * pass a closure to configure the remapper.
     * @since 0.2.3
     */
    fun setTinyRemapperConf(
        @DelegatesTo(
            value = ForgePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        tinyRemapperConf = {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

}