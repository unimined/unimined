package xyz.wagyourtail.unimined.internal.minecraft.patch.fabric

import org.gradle.api.Project
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable

open class OrnitheFabricMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider
): LegacyFabricMinecraftTransformer(project, provider) {

    override val defaultProdNamespace: String = "calamus"

    override fun addIntermediaryMappings() {
        provider.mappings {
            calamus()
        }
    }

}