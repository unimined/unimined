package xyz.wagyourtail.unimined.minecraft.mod

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.mappingio.format.ZipReader
import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.OutputConsumerPath.ResourceRemapper
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.mod.ModProvider
import xyz.wagyourtail.unimined.api.mod.ModRemapper
import xyz.wagyourtail.unimined.minecraft.patch.fabric.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.forge.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.refmap.BetterMixinExtension
import xyz.wagyourtail.unimined.util.getTempFilePath
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.name

class ModRemapperImpl(
    project: Project,
    val modProvider: ModProvider,
    val uniminedExtension: UniminedExtension
) : ModRemapper(project) {

    private val mcProvider by lazy { project.minecraft }
    private val mappings by lazy { project.mappings }

    private val preTransform = mutableMapOf<EnvType, List<File>>()

    override fun preTransform(envType: EnvType): List<File> =
        preTransform.getOrDefault(envType, emptyList())

    init {
        project.repositories.forEach { repo ->
            repo.content {
                it.excludeGroupByRegex("remapped_.+")
            }
        }
        project.repositories.flatDir { repo ->
            repo.dirs(modTransformFolder().toAbsolutePath().toString())
            repo.content {
                it.includeGroupByRegex("remapped_.+")
            }
        }
    }

    private fun constructRemapper(envType: EnvType, fromNs: MappingNamespace, toNs: MappingNamespace, mc: Path): Pair<TinyRemapper, BetterMixinExtension?> {
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(
                project.mappings.getMappingsProvider(
                    envType,
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
        val mixinExtension = if (remapMixins == MixinRemap.UNIMINED) {
            val mixin = BetterMixinExtension("error.mixin.refmap.json", project.gradle.startParameter.logLevel)
            remapperB.extension(mixin)
            mixin
        } else null
        if (remapMixins != MixinRemap.NONE) {
            remapperB.extension(MixinExtension(when (remapMixins) {
                MixinRemap.TINY_HARD -> setOf(MixinExtension.AnnotationTarget.HARD)
                MixinRemap.TINY_HARDSOFT -> setOf(MixinExtension.AnnotationTarget.HARD, MixinExtension.AnnotationTarget.SOFT)
                else -> throw InvalidUserDataException("Invalid MixinRemap value: $remapMixins")
            }))
        }
        tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        remapper.readClassPathAsync(
            *project.minecraft.mcLibraries.resolve().map { it.toPath() }.toTypedArray()
        )
        remapper.readClassPathAsync(mc)
        return remapper to mixinExtension
    }

    fun remap(envType: EnvType) {
        val config = when (envType) {
            EnvType.COMBINED -> modProvider.combinedConfig
            EnvType.CLIENT -> modProvider.clientConfig
            EnvType.SERVER -> modProvider.serverConfig
        }

        val prodNamespace = fromNamespace
        val devFallbackNamespace = toFallbackNamespace
        val devNamespace = toNamespace

        val path = MappingNamespace.calculateShortestRemapPathWithFallbacks(
            prodNamespace,
            prodNamespace,
            devFallbackNamespace,
            devNamespace,
            project.mappings.getAvailableMappings(envType)
        )
        project.logger.lifecycle("remapping from $prodNamespace to ${devNamespace}/${devFallbackNamespace}")
        project.logger.lifecycle("Calculating total mod dependency count...")
        val count = config.configurations.sumOf { it.dependencies.size }
        val last = path.last()
        project.logger.lifecycle("Found $count dependencies, remapping to $last")
        if (count == 0) return
        val configs = mutableMapOf<Configuration, Configuration>()
        for (c in config.configurations) {
            val newC = project.configurations.detachedConfiguration().apply {
                exclude(
                    mapOf(
                        "group" to "net.fabricmc",
                        "module" to "fabric-loader"
                    )
                )
            }
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
        preTransform[envType] = mods.values.toList()
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
            val mc = mcProvider.getMinecraftWithMapping(
                envType,
                mcNamespace,
                mcFallbackNamespace
            )
            val remapper = constructRemapper(envType, prevNamespace, step.second, mc)
            val tags = preRemapInternal(remapper.first, mods)
            mods.clear()
            mods.putAll(remapInternal(remapper, envType, tags, step == last, prevNamespace to step.second))
            prevNamespace = step.second
            prevPrevNamespace = mcNamespace
        }
        // copy back to original
        val combinedNames = mappings.getCombinedNames(envType)
        for ((newC, c) in configs) {
            for (artifact in newC.resolvedConfiguration.resolvedArtifacts) {
                val classifier = artifact.classifier?.let { "$it-" } ?: ""
                val output = "remapped_${artifact.moduleVersion.id.group}:${artifact.name}:${artifact.moduleVersion.id.version}:${classifier}mapped-${combinedNames}-${mcProvider.mcPatcher.devNamespace}"
                c.dependencies.add(
                    project.dependencies.create(
                        output
                    )
                )
            }
        }
    }

    private fun preRemapInternal(remapper: TinyRemapper, deps: Map<ResolvedArtifact, File>): Map<ResolvedArtifact, Pair<InputTag, File>> {
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

    private fun remapInternal(remapper: Pair<TinyRemapper, BetterMixinExtension?>, envType: EnvType, deps: Map<ResolvedArtifact, Pair<InputTag, File>>, final: Boolean, remap: Pair<MappingNamespace, MappingNamespace>): Map<ResolvedArtifact, File> {
        val output = mutableMapOf<ResolvedArtifact, File>()
        for ((artifact, tag) in deps) {
            output[artifact] = remapModInternal(remapper, envType, artifact, tag, final, remap).toFile()
        }
        remapper.first.finish()
        return output
    }

    private fun remapModInternal(remapper: Pair<TinyRemapper, BetterMixinExtension?>, envType: EnvType, dep: ResolvedArtifact, input: Pair<InputTag, File>, final: Boolean, remap: Pair<MappingNamespace, MappingNamespace>): Path {
        val combinedNames = mappings.getCombinedNames(envType)
        val target = if (final) {
            modTransformFolder().resolve("${dep.file.nameWithoutExtension}-mapped-${combinedNames}-${mcProvider.mcPatcher.devNamespace}.${dep.file.extension}")
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
                    AccessWidenerMinecraftTransformer.awRemapper(
                        remap.first.type.id,
                        remap.second.type.id
                    ), innerJarStripper, AccessTransformerMinecraftTransformer.atRemapper(remapAtToLegacy)
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
        return uniminedExtension.getLocalCache().resolve("modTransform").createDirectories()
    }

    private val innerJarStripper: ResourceRemapper = object : ResourceRemapper {
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
