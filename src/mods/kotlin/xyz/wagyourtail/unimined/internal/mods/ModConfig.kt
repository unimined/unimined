package xyz.wagyourtail.unimined.internal.mods

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.mappingio.format.ZipReader
import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.mod.ModRemapSettings
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.aw.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.BetterMixinExtension
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.getTempFilePath
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.name

class ModConfig(val project: Project, val provider: MinecraftConfig) : ModRemapSettings() {

    override var prodNamespace: MappingNamespace by FinalizeOnRead(LazyMutable { provider.mcPatcher.prodNamespace })

    override var devNamespace: MappingNamespace by FinalizeOnRead(LazyMutable { provider.mappings.devNamespace })

    override var devFallbackNamespace: MappingNamespace by FinalizeOnRead(LazyMutable { provider.mappings.devFallbackNamespace })

    override var remapAtToLegacy: Boolean by FinalizeOnRead(LazyMutable { (provider.mcPatcher as? ForgePatcher)?.remapAtToLegacy == true })

    var tinyRemapSettings: TinyRemapper.Builder.() -> Unit by FinalizeOnRead {}

    var config: Configuration.() -> Unit by FinalizeOnRead {
        exclude(
            mapOf(
                "group" to "net.fabricmc",
                "module" to "fabric-loader"
            )
        )
        exclude(
            mapOf(
                "group" to "net.legacyfabric",
                "module" to "fabric-loader"
            )
        )
        exclude(
            mapOf(
                "group" to "net.quiltmc",
                "module" to "quilt-loader"
            )
        )
    }

    private fun constructRemapper(
        fromNs: MappingNamespace,
        toNs: MappingNamespace,
        mc: Path
    ): Pair<TinyRemapper, BetterMixinExtension?> {
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(
                provider.mappings.getTRMappings(
                    fromNs to toNs,
                    false
                )
            )
            .skipLocalVariableMapping(true)
            .ignoreConflicts(true)
            .threads(Runtime.getRuntime().availableProcessors())
        val classpath = KotlinClasspathService.getOrCreateIfRequired(project)
        if (classpath != null) {
            remapperB.extension(KotlinRemapperClassloader.create(classpath).tinyRemapperExtension)
        }
        val mixinExtension = when (mixinRemap) {
            MixinRemap.UNIMINED -> {
                val mixin = BetterMixinExtension(
                    "error.mixin.refmap.json",
                    project.gradle.startParameter.logLevel,
                    fallbackWhenNotInJson = true
                )
                remapperB.extension(mixin)
                mixin
            }
            MixinRemap.NONE -> null
            MixinRemap.TINY_HARD, MixinRemap.TINY_HARDSOFT -> {
                remapperB.extension(
                    MixinExtension(
                        when (mixinRemap) {
                            MixinRemap.TINY_HARD -> setOf(MixinExtension.AnnotationTarget.HARD)
                            MixinRemap.TINY_HARDSOFT -> setOf(
                                MixinExtension.AnnotationTarget.HARD,
                                MixinExtension.AnnotationTarget.SOFT
                            )

                            else -> throw InvalidUserDataException("Invalid MixinRemap value: $mixinRemap")
                        }
                    )
                )
                null
            }
            else -> throw InvalidUserDataException("Invalid MixinRemap value: $mixinRemap")
        }
        tinyRemapSettings(remapperB)
        val remapper = remapperB.build()
        remapper.readClassPathAsync(
            *provider.minecraftLibraries.files.map { it.toPath() }.toTypedArray()
        )
        remapper.readClassPathAsync(mc)
        return remapper to mixinExtension
    }

    override fun remapper(remapperBuilder: TinyRemapper.Builder.() -> Unit) {
        tinyRemapSettings = remapperBuilder
    }

    fun doRemap(config: Set<Configuration>) {
        project.logger.lifecycle("[Unimined/ModRemapper] Remapping mods")
        val count = config.sumOf { it.dependencies.size }
        if (count == 0) {
            project.logger.lifecycle("[Unimined/ModRemapper] No mods found for remapping")
            return
        }

        project.logger.lifecycle("[Unimined/ModRemapper] Found $count mods for remapping")
        val path = MappingNamespace.calculateShortestRemapPathWithFallbacks(
            prodNamespace,
            prodNamespace,
            devFallbackNamespace,
            devNamespace,
            provider.mappings.available
        )
        project.logger.info("[Unimined/ModRemapper] Remapping from $prodNamespace to ${devNamespace}/${devFallbackNamespace} using $prodNamespace -> ${path.map { it.second }.joinToString(" -> ")}")
        val configs = mutableMapOf<Configuration, Configuration>()
        for (c in config) {
            val newC = project.configurations.detachedConfiguration().apply(this.config)
            configs[newC] = c
            // copy out dependencies
            newC.dependencies.addAll(c.dependencies)
            // remove from original
            c.dependencies.clear()
        }
        val mods = mutableMapOf<ResolvedArtifact, File>()
        for (c in configs.keys) {
            for (r in c.resolvedConfiguration.resolvedArtifacts) {
                if (r.extension != "jar") continue
                mods[r] = r.file
            }
        }
        val preTransform = mods.values.toList()
        var prevNamespace = prodNamespace
        var prevPrevNamespace = MappingNamespace.OFFICIAL
        for (i in path.indices) {
            val step = path[i]
            val mcNamespace = prevNamespace
            val mcFallbackNamespace = if (step.first) {
                step.second
            } else {
                prevPrevNamespace
            }
            val mc = provider.getMinecraft(
                mcNamespace,
                mcFallbackNamespace
            )
            val remapper = constructRemapper(prevNamespace, step.second, mc)
            val tags = preRemapInternal(remapper.first, mods)
            mods.clear()
            mods.putAll(
                remapInternal(
                    remapper,
                    tags,
                    step.second == devNamespace,
                    prevNamespace to step.second
                )
            )
            prevNamespace = step.second
            prevPrevNamespace = mcNamespace
        }
        // supply back to original configs
        for ((newC, c) in configs) {
            for (artifact in newC.resolvedConfiguration.resolvedArtifacts) {
                val classifier = artifact.classifier?.let { "$it-" } ?: ""
                val output = "remapped_${artifact.moduleVersion.id.group}:${artifact.name}:${artifact.moduleVersion.id.version}:${classifier}mapped-${provider.mappings.combinedNames}-${provider.mappings.devNamespace}-${provider.mappings.devFallbackNamespace}"
                c.dependencies.add(
                    project.dependencies.create(
                        output
                    )
                )
            }
        }
    }

    private fun preRemapInternal(
        remapper: TinyRemapper,
        deps: Map<ResolvedArtifact, File>
    ): Map<ResolvedArtifact, Pair<InputTag, File>> {
        val output = mutableMapOf<ResolvedArtifact, Pair<InputTag, File>>()
        for ((artifact, file) in deps) {
            val tag = remapper.createInputTag()
            if (file.isDirectory) {
                throw InvalidUserDataException("Cannot remap directory ${file.absolutePath}")
            } else {
                remapper.readInputsAsync(tag, file.toPath())
            }
            output[artifact] = tag to file
        }
        return output
    }

    private fun remapInternal(
        remapper: Pair<TinyRemapper, BetterMixinExtension?>,
        deps: Map<ResolvedArtifact, Pair<InputTag, File>>,
        final: Boolean,
        remap: Pair<MappingNamespace, MappingNamespace>
    ): Map<ResolvedArtifact, File> {
        val output = mutableMapOf<ResolvedArtifact, File>()
        for ((artifact, tag) in deps) {
            output[artifact] = remapModInternal(remapper, artifact, tag, final, remap).toFile()
        }
        remapper.first.finish()
        return output
    }

    private fun remapModInternal(
        remapper: Pair<TinyRemapper, BetterMixinExtension?>,
        dep: ResolvedArtifact,
        input: Pair<InputTag, File>,
        final: Boolean,
        remap: Pair<MappingNamespace, MappingNamespace>
    ): Path {
        val combinedNames = provider.mappings.combinedNames
        val target = if (final) {
            modTransformFolder().resolve("${dep.file.nameWithoutExtension}-mapped-${combinedNames}-${provider.mappings.devNamespace}-${provider.mappings.devFallbackNamespace}.${dep.file.extension}")
        } else {
            getTempFilePath(dep.file.nameWithoutExtension, ".jar")
        }
        if (remapper.second != null) {
            remapper.second!!.reset(dep.name + ".mixin-refmap.json")
        }
        OutputConsumerPath.Builder(target).build().use {
            it.addNonClassFiles(
                input.second.toPath(),
                remapper.first,
                listOf(
                    AccessWidenerMinecraftTransformer.AwRemapper(
                        remap.first.type.id,
                        remap.second.type.id
                    ),
                    innerJarStripper,
                    AccessTransformerMinecraftTransformer.AtRemapper(project.logger, remapAtToLegacy)
                ) + NonClassCopyMode.FIX_META_INF.remappers + (
                        if (remapper.second != null) {
                            listOf(remapper.second!!)
                        } else {
                            emptyList()
                        }
                        )
            )
            remapper.first.apply(it, input.first)
        }
        if (remapper.second != null) {
            ZipReader.openZipFileSystem(target, mapOf("mutable" to true)).use {
                remapper.second!!.write(it)
            }
        }
        return target
    }

    private fun modTransformFolder(): Path {
        return project.unimined.getLocalCache().resolve("modTransform").createDirectories()
    }

    private val innerJarStripper: OutputConsumerPath.ResourceRemapper = object: OutputConsumerPath.ResourceRemapper {
        override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
            return relativePath.name.contains(".mod.json")
        }

        override fun transform(
            destinationDirectory: Path,
            relativePath: Path,
            input: InputStream,
            remapper: TinyRemapper
        ) {
            val output = destinationDirectory.resolve(relativePath)
            output.parent.createDirectories()
            BufferedReader(InputStreamReader(input)).use { reader ->
                val json = JsonParser.parseReader(reader)
                json.asJsonObject.remove("jars")
                json.asJsonObject.remove("quilt_loader")
                BufferedWriter(
                    OutputStreamWriter(
                        BufferedOutputStream(
                            Files.newOutputStream(
                                output,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING
                            )
                        )
                    )
                ).use { writer ->
                    GsonBuilder().setPrettyPrinting().create().toJson(json, writer)
                }
            }
        }
    }
}