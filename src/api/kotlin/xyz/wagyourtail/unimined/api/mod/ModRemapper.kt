package xyz.wagyourtail.unimined.api.mod

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.util.LazyMutable
import java.io.File

/**
 * The class responsible for remapping mods.
 * @since 0.2.3
 */
abstract class ModRemapper(val project: Project) {

    /**
     * namespace the mod is currently in. (prod)
     */
    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    var fromNamespace by LazyMutable { project.minecraft.mcPatcher.prodNamespace }

    /**
     * namespace the mod should be remapped to. (dev)
     */
    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    var toNamespace by LazyMutable { project.minecraft.mcPatcher.devNamespace }

    /**
     * fallback namespace the mod should be remapped to. (devFallback)
     */
    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    var toFallbackNamespace by LazyMutable { project.minecraft.mcPatcher.devFallbackNamespace }

    /**
     * @since 0.4.0
     */
    val remapAtToLegacy: Boolean = false


    fun setFromNamespace(namespace: String) {
        fromNamespace = MappingNamespace.getNamespace(namespace)
    }

    fun setToNamespace(namespace: String) {
        toNamespace = MappingNamespace.getNamespace(namespace)
    }

    fun setToFallbackNamespace(namespace: String) {
        toFallbackNamespace = MappingNamespace.getNamespace(namespace)
    }


    /**
     * pass a closure to configure the remapper seperately from the one in MinecraftRemapper.
     * it defaults to whatever MinecraftRemapper has set for it.
     * @since 0.3.3
     */
    @set:ApiStatus.Experimental
    var tinyRemapperConf: (TinyRemapper.Builder) -> Unit by LazyMutable { project.minecraft.mcRemapper.tinyRemapperConf }

    /**
     * pass a closure to configure the remapper.
     * @since 0.3.3
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

    abstract fun preTransform(envType: EnvType): List<File>
}