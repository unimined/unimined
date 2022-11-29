package xyz.wagyourtail.unimined.api.mod

import org.gradle.api.artifacts.Configuration
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.EnvType
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

    @ApiStatus.Internal
    abstract fun internalModRemapperConfiguration(envType: EnvType): Configuration

}