package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace

/**
 * @since 0.4.10
 */
interface MergedPatcher: MinecraftPatcher {
    override var prodNamespace: MappingNamespace

    fun setProdNamespace(namespace: String) {
        prodNamespace = MappingNamespace.getNamespace(namespace)
    }

    /**
     * enables the fabric patcher.
     * @param action the action to configure the patcher.
     * @since 0.4.10
     */
    fun fabric(action: (FabricLikePatcher) -> Unit)

    /**
     * enables the fabric patcher.
     * @param action the action to perform on the patcher.
     * @since 0.4.10
     */
    fun fabric(
        @DelegatesTo(
            value = FabricLikePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        fabric {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the fabric patcher.
     * @since 0.4.10
     */
    fun fabric() {
        fabric {}
    }

    /**
     * enables the fabric patcher with additional tweaks for LegacyFabric.
     * @param action the action to perform on the patcher.
     * @since 0.4.10
     */
    fun legacyFabric(action: (FabricLikePatcher) -> Unit)

    /**
     * enables the fabric patcher with additional tweaks for LegacyFabric.
     * @param action the action to perform on the patcher.
     * @since 0.4.10
     */
    fun legacyFabric(
        @DelegatesTo(
            value = FabricLikePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        legacyFabric {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the fabric patcher with additional tweaks for LegacyFabric.
     * @since 0.4.10
     */
    fun legacyFabric() {
        legacyFabric {}
    }

    /**
     * enables the quilt patcher.
     * @param action the action to configure the patcher.
     * @since 0.4.10
     */
    fun quilt(action: (FabricLikePatcher) -> Unit)

    /**
     * enables the quilt patcher.
     * @param action the action to perform on the patcher.
     * @since 0.4.10
     */
    fun quilt(
        @DelegatesTo(
            value = FabricLikePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        quilt {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the quilt patcher.
     * @since 0.4.10
     */
    fun quilt() {
        quilt {}
    }

    /**
     * enables the forge patcher.
     * @param action the action to configure the patcher.
     * @since 0.4.10
     */
    fun forge(action: (ForgePatcher) -> Unit)

    /**
     * enables the forge patcher.
     * @param action the action to perform on the patcher.
     * @since 0.4.10
     */
    fun forge(
        @DelegatesTo(
            value = ForgePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        forge {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the forge patcher.
     * @since 0.4.10
     */
    fun forge() {
        forge {}
    }

    /**
     * enables the jar mod patcher.
     * @param action the action to configure the patcher.
     * @since 0.4.10
     */
    fun jarMod(action: (JarModPatcher) -> Unit)

    /**
     * enables the jar mod patcher.
     * @param action the action to perform on the patcher.
     * @since 0.4.10
     */
    fun jarMod(
        @DelegatesTo(
            value = JarModPatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        jarMod {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the jar mod patcher.
     * @since 0.4.10
     */
    fun jarMod() {
        jarMod {}
    }
}