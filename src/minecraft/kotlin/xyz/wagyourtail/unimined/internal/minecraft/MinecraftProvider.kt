package xyz.wagyourtail.unimined.internal.minecraft

import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.gradle.configurationcache.extensions.capitalized
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.patch.MergedPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.FabricLikePatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.JarModPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.MinecraftPatcher
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.internal.mapping.MappingsProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.NoTransformMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.LegacyFabricMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.OfficialFabricMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.QuiltMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.resolver.MinecraftDownloader
import xyz.wagyourtail.unimined.internal.mods.ModsProvider
import xyz.wagyourtail.unimined.internal.runs.RunsProvider
import xyz.wagyourtail.unimined.util.*
import java.io.File
import java.nio.file.Path

class MinecraftProvider(project: Project, sourceSet: SourceSet) : MinecraftConfig(project, sourceSet) {
    override var mcPatcher: MinecraftPatcher by FinalizeOnRead(ChangeOnce(NoTransformMinecraftTransformer(project, this)))

    override val mappings = MappingsProvider(project, this)

    override val mods = ModsProvider(project, this)

    override val runs = RunsProvider(project, this)

    override val minecraftData = MinecraftDownloader(project, this)

    val minecraftRemapper = MinecraftRemapper(project, this)

    val minecraft: Configuration = project.configurations.maybeCreate("minecraft".withSourceSet(sourceSet)).also {
        it.extendsFrom(project.configurations.getByName("implementation".withSourceSet(sourceSet)))
    }

    val minecraftLibraries: Configuration = project.configurations.maybeCreate("minecraftLibraries".withSourceSet(sourceSet)).also {
        it.extendsFrom(project.configurations.getByName("implementation".withSourceSet(sourceSet)))
    }

    override fun remap(task: Task, action: RemapJarTask.() -> Unit) {
        TODO("Not yet implemented")
    }

    override fun remap(task: Task, name: String, action: RemapJarTask.() -> Unit) {
        TODO("Not yet implemented")
    }

    private val minecraftFiles: Map<Pair<MappingNamespace, MappingNamespace>, Path> = defaultedMapOf {
        val mc = if (side == EnvType.COMBINED) {
            val client = minecraftData.minecraftClient
            val server = minecraftData.minecraftServer
            (mcPatcher as AbstractMinecraftTransformer).merge(client, server)
        } else {
            minecraftData.getMinecraft(side)
        }
        (mcPatcher as AbstractMinecraftTransformer).afterRemap(
            minecraftRemapper.provide((mcPatcher as AbstractMinecraftTransformer).transform(mc), it.first, it.second)
        ).path
    }

    override fun getMinecraft(namespace: MappingNamespace, fallbackNamespace: MappingNamespace): Path {
        return minecraftFiles[namespace to fallbackNamespace] ?: error("minecraft file not found for $namespace")
    }

    override fun remapper(remapperBuilder: TinyRemapper.Builder.() -> Unit) {
        minecraftRemapper.tinyRemapperConf = remapperBuilder
    }

    override fun merged(action: (MergedPatcher) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun fabric(action: (FabricLikePatcher) -> Unit) {
        mcPatcher = OfficialFabricMinecraftTransformer(project, this).also(action)
    }

    override fun legacyFabric(action: (FabricLikePatcher) -> Unit) {
        mcPatcher = LegacyFabricMinecraftTransformer(project, this).also(action)
    }

    override fun quilt(action: (FabricLikePatcher) -> Unit) {
        mcPatcher = QuiltMinecraftTransformer(project, this).also(action)
    }

    override fun forge(action: (ForgePatcher) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun jarMod(action: (JarModPatcher) -> Unit) {
        TODO("Not yet implemented")
    }

    val minecraftDepName: String = project.path.replace(":", "_").let { projectPath ->
        "minecraft${if (projectPath == "_") "" else projectPath}${if (sourceSet.name == "main") "" else "+"+sourceSet.name.capitalized()}"
    }

    fun apply() {
        // ensure minecraft deps are clear
        if (minecraft.dependencies.isNotEmpty()) {
            project.logger.warn("[Unimined/minecraft] $minecraft dependencies are not empty! clearing...")
            minecraft.dependencies.clear()
        }
        if (minecraftLibraries.dependencies.isNotEmpty()) {
            project.logger.warn("[Unimined/minecraft] $minecraftLibraries dependencies are not empty! clearing...")
            minecraftLibraries.dependencies.clear()
        }

        // add minecraft deps
        minecraft.dependencies.add(project.dependencies.create("net.minecraft:$minecraftDepName:$version" + if (side != EnvType.COMBINED) ":${side.classifier}" else ""))

        TODO("minecraft library deps & verify subconfigs")
        (mcPatcher as AbstractMinecraftTransformer).apply()
        mods.apply()
    }

    val minecraftFileDev: File by lazy {
        getMinecraft(mappings.devNamespace, mappings.devFallbackNamespace).toFile()
    }

}