package xyz.wagyourtail.unimined.api.mod

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.artifacts.Configuration
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.mixin.MixinRemapOptions
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable

abstract class ModRemapConfig(val configurations: Set<Configuration>) {

    @set:ApiStatus.Internal
    abstract var namespace: MappingNamespaceTree.Namespace

    @set:ApiStatus.Internal
    abstract var fallbackNamespace: MappingNamespaceTree.Namespace

    abstract fun namespace(ns: String)

    abstract fun fallbackNamespace(ns: String)

    abstract fun catchAWNamespaceAssertion()

    @set:ApiStatus.Experimental
    abstract var remapAtToLegacy: Boolean

    /**
     * @since 1.1.0
     */
    abstract fun mixinRemap(action: MixinRemapOptions.() -> Unit)

    /**
     * @since 1.1.0
     */
    fun mixinRemap(
        @DelegatesTo(MixinRemapOptions::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mixinRemap {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }


    @ApiStatus.Experimental
    abstract fun remapper(remapperBuilder: TinyRemapper.Builder.() -> Unit)
}