package xyz.wagyourtail.unimined.minecraft.patch

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.util.LazyMutable

class NoTransformMinecraftTransformer(project: Project, provider: MinecraftProviderImpl) : AbstractMinecraftTransformer(
    project,
    provider
) {

    override var prodNamespace: MappingNamespace = MappingNamespace.OFFICIAL

    override var devNamespace: MappingNamespace by LazyMutable { MappingNamespace.findByType(MappingNamespace.Type.NAMED, project.mappings.getAvailableMappings(project.minecraft.defaultEnv)) }
    override var devFallbackNamespace: MappingNamespace by LazyMutable { MappingNamespace.findByType(MappingNamespace.Type.INT, project.mappings.getAvailableMappings(project.minecraft.defaultEnv)) }
}