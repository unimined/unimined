package xyz.wagyourtail.unimined.api.minecraft

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.gradle.api.provider.Property
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.FabricPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.JarModPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.MinecraftPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.reamp.MinecraftRemapper
import java.io.File

@Suppress("LeakingThis")
abstract class MinecraftProvider(val project: Project) {
    abstract val mcRemapper: MinecraftRemapper
    abstract val mcPatcher: MinecraftPatcher

    abstract val overrideMainClassClient: Property<String?>
    abstract val overrideMainClassServer: Property<String?>

    abstract val clientWorkingDirectory: Property<File>
    abstract val serverWorkingDirectory: Property<File>

    abstract val disableCombined: Property<Boolean>

    abstract val alphaServerVersionOverride: Property<String?>


    init {
        overrideMainClassClient.convention(null as String?).finalizeValueOnRead()
        overrideMainClassServer.convention(null as String?).finalizeValueOnRead()

        clientWorkingDirectory.convention(project.projectDir.resolve("run").resolve("client")).finalizeValueOnRead()
        serverWorkingDirectory.convention(project.projectDir.resolve("run").resolve("server")).finalizeValueOnRead()

        disableCombined.convention(false).finalizeValueOnRead()

        alphaServerVersionOverride.convention(null as String?).finalizeValueOnRead()
    }

    abstract fun fabric(action: (FabricPatcher) -> Unit)

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

    fun fabric() {
        fabric {}
    }

    abstract fun forge(action: (ForgePatcher) -> Unit)

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

    fun forge() {
        forge {}
    }

    abstract fun jarMod(action: (JarModPatcher) -> Unit)

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

    fun jarMod() {
        jarMod {}
    }
}