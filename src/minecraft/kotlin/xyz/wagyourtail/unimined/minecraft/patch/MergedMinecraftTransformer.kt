package xyz.wagyourtail.unimined.minecraft.patch

import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.FabricLikePatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.JarModPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.MergedPatcher
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.fabric.LegacyFabricMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.fabric.OfficialFabricMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.fabric.QuiltMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.forge.ForgeMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.util.LazyMutable
import java.nio.file.Path

class MergedMinecraftTransformer(project: Project, provider: MinecraftProviderImpl): AbstractMinecraftTransformer(
    project,
    provider,
    "merged"
), MergedPatcher {

    @get:ApiStatus.Internal
    val patchers = mutableListOf<AbstractMinecraftTransformer>()

    override var prodNamespace: MappingNamespace by LazyMutable {
        patchers.first().prodNamespace
    }

    override var devNamespace: MappingNamespace by LazyMutable {
        patchers.first().devNamespace
    }

    override var devFallbackNamespace: MappingNamespace by LazyMutable {
        patchers.first().devFallbackNamespace
    }

    override fun fabric(action: (FabricLikePatcher) -> Unit) {
        patchers.add(OfficialFabricMinecraftTransformer(project, provider).also(action))
    }

    override fun legacyFabric(action: (FabricLikePatcher) -> Unit) {
        patchers.add(LegacyFabricMinecraftTransformer(project, provider).also(action))
    }

    override fun quilt(action: (FabricLikePatcher) -> Unit) {
        patchers.add(QuiltMinecraftTransformer(project, provider).also(action))
    }

    override fun forge(action: (ForgePatcher) -> Unit) {
        patchers.add(ForgeMinecraftTransformer(project, provider).also(action))
    }

    override fun jarMod(action: (JarModPatcher) -> Unit) {
        patchers.add(JarModMinecraftTransformer(project, provider).also(action))
    }

    override fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar): MinecraftJar {
        return patchers.first().merge(clientjar, serverjar)
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        var last = minecraft
        for (patcher in patchers) {
            last = patcher.transform(last)
        }
        return last
    }

    override fun applyLaunches() {
        for (patcher in patchers) {
            patcher.applyLaunches()
        }
    }

    override fun afterEvaluate() {
        for (patcher in patchers) {
            patcher.afterEvaluate()
        }
    }

    override fun afterRemapJarTask(output: Path) {
        for (patcher in patchers) {
            patcher.afterRemapJarTask(output)
        }
    }
}