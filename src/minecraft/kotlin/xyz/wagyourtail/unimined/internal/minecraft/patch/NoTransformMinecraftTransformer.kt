package xyz.wagyourtail.unimined.internal.minecraft.patch

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider

class NoTransformMinecraftTransformer(project: Project, provider: MinecraftProvider): AbstractMinecraftTransformer(
    project,
    provider,
    "none"
) {

    override var prodNamespace: MappingNamespaceTree.Namespace = provider.mappings.OFFICIAL

}