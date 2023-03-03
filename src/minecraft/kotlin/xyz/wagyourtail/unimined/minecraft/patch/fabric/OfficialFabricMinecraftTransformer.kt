package xyz.wagyourtail.unimined.minecraft.patch.fabric

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl

class OfficialFabricMinecraftTransformer(
    project: Project,
    provider: MinecraftProviderImpl
) : FabricMinecraftTransformer(project, provider, Constants.FABRIC_PROVIDER) {
}