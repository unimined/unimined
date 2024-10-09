package xyz.wagyourtail.unimined.internal.minecraft.patch

import org.gradle.api.Project
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.util.LazyMutable

class NoTransformMinecraftTransformer(project: Project, provider: MinecraftProvider): AbstractMinecraftTransformer(
    project,
    provider,
    "none"
) {

}