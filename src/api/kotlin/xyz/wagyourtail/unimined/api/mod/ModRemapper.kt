package xyz.wagyourtail.unimined.api.mod

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.artifacts.Configuration
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.util.LazyMutable

/**
 * The class responsible for remapping mods.
 * @since 0.2.3
 */
abstract class ModRemapper(val provider: ModProvider) {

    /**
     * namespace the mod is currently in. (prod)
     */
    var fromNamespace by LazyMutable { provider.parent.minecraftProvider.mcPatcher.prodNamespace }

    /**
     * fallback namespace the mod is currently in. (prodFallback)
     */
    var fromFallbackNamespace by LazyMutable { provider.parent.minecraftProvider.mcPatcher.prodFallbackNamespace }

    /**
     * namespace the mod should be remapped to. (dev)
     */
    var toNamespace by LazyMutable { provider.parent.minecraftProvider.mcPatcher.devNamespace }

    /**
     * fallback namespace the mod should be remapped to. (devFallback)
     */
    var toFallbackNamespace by LazyMutable { provider.parent.minecraftProvider.mcPatcher.devFallbackNamespace }


    /**
     * pass a closure to configure the remapper seperately from the one in MinecraftRemapper.
     * it defaults to whatever MinecraftRemapper has set for it.
     * @since 0.2.3
     */
    abstract var tinyRemapperConf: (TinyRemapper.Builder) -> Unit

    /**
     * pass a closure to configure the remapper.
     * @since 0.2.3
     */
    fun setTinyRemapperConf(@DelegatesTo(
        value = ForgePatcher::class,
        strategy = Closure.DELEGATE_FIRST
    ) action: Closure<*>
    ){
        tinyRemapperConf = {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Internal
    abstract fun internalModRemapperConfiguration(envType: EnvType): Configuration

}