package xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.patch.bukkit.SpigotPatcher
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider

class SpigotMinecraftTransformer(project: Project,
                                 provider: MinecraftProvider
) : CraftbukkitMinecraftTransformer(
    project,
    provider,
    "spigot"
), SpigotPatcher {



}
