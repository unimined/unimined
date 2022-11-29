package xyz.wagyourtail.unimined.api.minecraft

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Property
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.FabricPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.JarModPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.MinecraftPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.reamp.MinecraftRemapper
import java.io.File
import java.nio.file.Path

/**
 * The main interface for interacting with minecraft.
 * @property project The project this extension is attached to.
 * @since 0.1.0
 */
@Suppress("LeakingThis")
abstract class MinecraftProvider<T: MinecraftRemapper, U : MinecraftPatcher>(val project: Project) {
    @get:ApiStatus.Internal
    abstract val minecraft: MinecraftResolver

    /**
     * The class responsible for remapping minecraft.
     * @since 0.2.3
     */
    abstract val mcRemapper: T

    /**
     * The class responsible for patching minecraft.
     * @since 0.2.3
     */
    abstract var mcPatcher: U

    abstract val overrideMainClassClient: Property<String?>
    abstract val overrideMainClassServer: Property<String?>


    abstract val clientWorkingDirectory: Property<File>
    abstract val serverWorkingDirectory: Property<File>

    /**
     * disables the combined mc jar
     */
    @get:ApiStatus.Experimental
    abstract val disableCombined: Property<Boolean>

    /**
     * set the mc version for server seperately from client.
     */
    abstract val alphaServerVersionOverride: Property<String?>

    @get:ApiStatus.Internal
    abstract val mcLibraries: Configuration

    init {
        overrideMainClassClient.convention(null as String?).finalizeValueOnRead()
        overrideMainClassServer.convention(null as String?).finalizeValueOnRead()

        clientWorkingDirectory.convention(project.projectDir.resolve("run").resolve("client")).finalizeValueOnRead()
        serverWorkingDirectory.convention(project.projectDir.resolve("run").resolve("server")).finalizeValueOnRead()

        disableCombined.convention(false).finalizeValueOnRead()

        alphaServerVersionOverride.convention(null as String?).finalizeValueOnRead()
    }

    /**
     * enables the fabric patcher.
     * @param action the action to configure the patcher.
     * @since 0.1.0
     */
    abstract fun fabric(action: (FabricPatcher) -> Unit)

    /**
     * enables the fabric patcher.
     * @param action the action to perform on the patcher.
     * @since 0.1.0
     */
    fun fabric(
        @DelegatesTo(
            value = FabricPatcher::class,
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
     * @since 0.1.0
     */
    fun fabric() {
        fabric {}
    }

    /**
     * enables the forge patcher.
     * @param action the action to configure the patcher.
     * @since 0.1.0
     */
    abstract fun forge(action: (ForgePatcher) -> Unit)

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
            action.delegate = it
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
     * @since 0.1.0
     */
    abstract fun jarMod(action: (JarModPatcher) -> Unit)

    /**
     * enables the jar mod patcher.
     * @param action the action to perform on the patcher.
     * @since 0.1.0
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
     * @since 0.1.0
     */
    fun jarMod() {
        jarMod {}
    }

    @ApiStatus.Internal
    abstract fun getMinecraftWithMapping(envType: EnvType, namespace: String, fallbackNamespace: String): Path
}