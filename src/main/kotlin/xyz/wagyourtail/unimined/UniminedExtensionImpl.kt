package xyz.wagyourtail.unimined

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.launch.LauncherProvierImpl
import xyz.wagyourtail.unimined.mappings.MappingsProviderImpl
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.mod.ModProviderImpl

@Suppress("LeakingThis")
abstract class UniminedExtensionImpl(project: Project) : UniminedExtension(project) {

    override val minecraftProvider = project.extensions.create(
        "minecraft",
        MinecraftProviderImpl::class.java,
        project,
        this
    )

    override val mappingsProvider = project.extensions.create(
        "mappings",
        MappingsProviderImpl::class.java,
        project
    )


    override val modProvider = project.extensions.create(
        "mods",
        ModProviderImpl::class.java,
        project,
        this
    )

//    override val launcherProvider = project.extensions.create(
//        "launcher",
//        LauncherProvierImpl::class.java,
//        project,
//        this
//    )

}