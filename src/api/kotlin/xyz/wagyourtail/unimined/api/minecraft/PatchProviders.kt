package xyz.wagyourtail.unimined.api.minecraft

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import xyz.wagyourtail.unimined.api.minecraft.patch.FabricLikePatcher
import xyz.wagyourtail.unimined.api.minecraft.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.minecraft.patch.JarModAgentPatcher

/**
 * usage:
 *
 *
 * @since 0.4.10
 */
interface PatchProviders {

    /**
     * enables the fabric patcher.
     * @param action the action to configure the patcher.
     * @see FabricLikePatcher
     * @since 0.1.0
     */
    fun fabric(action: FabricLikePatcher.() -> Unit)

    /**
     * enables the fabric patcher.
     * @param action the action to perform on the patcher.
     * @since 0.1.0
     */
    fun fabric(
        @DelegatesTo(
            value = FabricLikePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        fabric {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the fabric patcher.
     * @since 0.1.0
     */
    fun fabric() {
        fabric {}
    }

    /**
     * enables the fabric patcher with additional tweaks for LegacyFabric.
     * @param action the action to perform on the patcher.
     * @see FabricLikePatcher
     * @since 0.4.2
     */
    fun legacyFabric(action: FabricLikePatcher.() -> Unit)

    /**
     * enables the fabric patcher with additional tweaks for LegacyFabric.
     * @param action the action to perform on the patcher.
     * @since 0.4.2
     */
    fun legacyFabric(
        @DelegatesTo(
            value = FabricLikePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        legacyFabric {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the fabric patcher with additional tweaks for LegacyFabric.
     * @since 0.4.2
     */
    fun legacyFabric() {
        legacyFabric {}
    }

    /**
     * enables the fabric patcher with additional tweaks for babric.
     * @param action the action to perform on the patcher.
     * @since 1.0.0
     */
    fun babric(action: FabricLikePatcher.() -> Unit)

    /**
     * enables the fabric patcher with additional tweaks for babric.
     * @param action the action to perform on the patcher.
     * @since 1.0.0
     */
    fun babric(
        @DelegatesTo(
            value = FabricLikePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        babric {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the fabric patcher with additional tweaks for babric.
     * @since 1.0.0
     */
    fun babric() {
        babric {}
    }

    /**
     * enables the quilt patcher.
     * @param action the action to configure the patcher.
     * @see FabricLikePatcher
     * @since 0.3.4
     */
    fun quilt(action: FabricLikePatcher.() -> Unit)

    /**
     * enables the quilt patcher.
     * @param action the action to perform on the patcher.
     * @since 0.3.4
     */
    fun quilt(
        @DelegatesTo(
            value = FabricLikePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        quilt {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the quilt patcher.
     * @since 0.3.4
     * @since 0.3.4
     */
    fun quilt() {
        quilt {}
    }

    /**
     * enables the forge patcher.
     * @param action the action to configure the patcher.
     * @see ForgePatcher
     * @since 0.1.0
     */
    fun forge(action: ForgePatcher.() -> Unit)

    /**
     * enables the forge patcher.
     * @param action the action to perform on the patcher.
     * @since 0.1.0
     */
    fun forge(
        @DelegatesTo(
            value = ForgePatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        forge {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the forge patcher.
     * @since 0.1.0
     */
    fun forge() {
        forge {}
    }

    /**
     * enables the jar mod patcher.
     * @param action the action to configure the patcher.
     * @see JarModAgentPatcher
     * @since 0.1.0
     */
    fun jarMod(action: JarModAgentPatcher.() -> Unit)

    /**
     * enables the jar mod patcher.
     * @param action the action to perform on the patcher.
     * @since 0.1.0
     */
    fun jarMod(
        @DelegatesTo(
            value = JarModAgentPatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        jarMod {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * enables the jar mod patcher.
     * @since 0.1.0
     */
    fun jarMod() {
        jarMod {}
    }
}
