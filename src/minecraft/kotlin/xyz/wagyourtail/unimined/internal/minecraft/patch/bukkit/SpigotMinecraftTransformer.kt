package xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.patch.bukkit.SpigotPatcher
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit.buildtools.BuildToolsExecutor

class SpigotMinecraftTransformer(project: Project,
                                 provider: MinecraftProvider
) : CraftbukkitMinecraftTransformer(
    project,
    provider,
    "spigot"
), SpigotPatcher {

    init {
        target = BuildToolsExecutor.BuildTarget.SPIGOT
    }

//    override val exclude: Set<String> = super.exclude + setOf(
//        "bukkit"
//    )

}
