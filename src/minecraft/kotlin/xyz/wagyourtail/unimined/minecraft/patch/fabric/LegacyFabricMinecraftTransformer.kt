package xyz.wagyourtail.unimined.minecraft.patch.fabric

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.fabric.LegacyFabricApiExtension
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import java.net.URI

class LegacyFabricMinecraftTransformer(
    project: Project,
    provider: MinecraftProviderImpl
) : FabricMinecraftTransformer(project, provider) {
    override fun setupApiExtension() {
        LegacyFabricApiExtension.apply(project)
    }

    override fun addMavens() {
        super.addMavens()

        project.repositories.maven {
            it.url = URI.create("https://repo.legacyfabric.net/repository/legacyfabric")
        }
    }
}