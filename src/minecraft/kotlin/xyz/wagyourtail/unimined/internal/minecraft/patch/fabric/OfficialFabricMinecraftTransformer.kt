package xyz.wagyourtail.unimined.internal.minecraft.patch.fabric

import org.gradle.api.Project
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider

class OfficialFabricMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider
): FabricMinecraftTransformer(project, provider) {
}