package xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.patch.bukkit.CraftbukkitPatcher
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar

open class CraftbukkitMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider,
    providerName: String = "craftbukkit"
) : AbstractMinecraftTransformer(project, provider, providerName), CraftbukkitPatcher {

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        if (minecraft.envType != EnvType.SERVER) throw IllegalArgumentException("Craftbukkit can only be applied to server jars")



        return super.transform(minecraft)
    }

}