package xyz.wagyourtail.unimined

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer

@Suppress("LeakingThis")
abstract class UniminedExtension(project: Project) {
    abstract val disableCombined : Property<Boolean>
    abstract val patchSettings : Property<Action<AbstractMinecraftTransformer>>
    abstract val launchArguments: ListProperty<String>

    init {
        disableCombined.convention(false).finalizeValueOnRead()
        patchSettings.convention {}.finalizeValueOnRead()
        launchArguments.convention(mutableListOf()).finalizeValueOnRead()
    }
}

