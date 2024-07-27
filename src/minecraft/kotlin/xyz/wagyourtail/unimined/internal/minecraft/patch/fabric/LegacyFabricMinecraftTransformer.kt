package xyz.wagyourtail.unimined.internal.minecraft.patch.fabric

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.SemVerUtils

open class LegacyFabricMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider
): FabricMinecraftTransformer(project, provider) {

    override fun addIntermediaryMappings() {
        provider.mappings {
            legacyIntermediary()
        }
    }

    override var prodNamespace: Namespace by FinalizeOnRead(LazyMutable {
        provider.mappings.checkedNs("legacyIntermediary")
    })

    override fun loader(dep: Any, action: Dependency.() -> Unit) {
        fabric.dependencies.add(
            (if (dep is String && !dep.contains(":")) {
                project.dependencies.create("net.fabricmc:fabric-loader:$dep")
            } else project.dependencies.create(dep)).apply(action)
        )
    }

    override fun addMavens() {
        super.addMavens()
        project.unimined.legacyFabricMaven()
    }

    override fun configureRemapJar(task: RemapJarTask) {
        if (fabricDep.version?.let { SemVerUtils.matches(it, ">=0.15.0") } == true) {
            project.logger.info("enabling mixin extra")
            task.mixinRemap {
                enableMixinExtra()
            }
        }
    }
}