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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.mod.ModRemapConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.aw.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.BetterMixinExtension
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.nonNullValues
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name

class ModRemapProvider(config: Set<Configuration>, val project: Project, val provider: MinecraftConfig) : ModRemapConfig(config) {

    override var namespace: MappingNamespace by FinalizeOnRead(LazyMutable { provider.mcPatcher.prodNamespace })

    override var fallbackNamespace: MappingNamespace by FinalizeOnRead(LazyMutable { provider.mcPatcher.prodNamespace })

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

    private val originalDeps = defaultedMapOf<Configuration, Set<Dependency>> {
        project.logger.info("[Unimined/ModRemapper] Original Deps: $it")
        for (dep in it.dependencies) {
            project.logger.info("[Unimined/ModRemapper]    $dep")
        }
        it.dependencies.toSet()
    }

    private val originalDepsFiles = defaultedMapOf<Configuration, Map<ResolvedArtifact, File>> {
        val detached = project.configurations.detachedConfiguration().apply(this.config)
        detached.dependencies.addAll(originalDeps[it])
        val resolved = mutableMapOf<ResolvedArtifact, File>()
        project.logger.info("[Unimined/ModRemapper] Original Dep Files: $it")
        for (r in detached.resolvedConfiguration.resolvedArtifacts) {
            if (r.extension == "pom") continue
            project.logger.info("[Unimined/ModRemapper]    $r -> ${r.file}")
            resolved[r] = r.file
        }
        resolved
    }

    fun getConfigForFile(file: File): Configuration? {
        val name = file.nameWithoutExtension.substringBefore("-mapped-${provider.mappings.combinedNames}")
        for ((c, artifacts) in originalDepsFiles) {
            for (r in artifacts.values) {
                if (r.nameWithoutExtension == name) {
                    project.logger.debug("[Unimined/ModRemapper] $file is an output of $c")
                    return c
                }
            }
        }
        return null
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

    fun doRemap(
        devNamespace: MappingNamespace = provider.mappings.devNamespace,
        devFallbackNamespace: MappingNamespace = provider.mappings.devFallbackNamespace,
        targetConfigurations: Map<Configuration, Configuration> = defaultedMapOf { it }
    ) {
        val path = MappingNamespace.calculateShortestRemapPathWithFallbacks(
            namespace,
            fallbackNamespace,
            devFallbackNamespace,
            devNamespace,
            provider.mappings.available
        )
        if (path.isNotEmpty()) {
            project.logger.lifecycle("[Unimined/ModRemapper] Remapping mods from $namespace/$fallbackNamespace to $devNamespace/$devFallbackNamespace")

            // resolve original dep files
            val count = configurations.sumOf { originalDepsFiles[it].size }
            if (count == 0) {
                project.logger.lifecycle("[Unimined/ModRemapper] No mods found for remapping")
                return
            }
            project.logger.lifecycle("[Unimined/ModRemapper] Found $count mods for remapping")
            project.logger.info(
                "[Unimined/ModRemapper] Remap path $namespace -> ${
                    path.map { it.second }.joinToString(" -> ")
                }"
            )
            val mods = mutableMapOf<ResolvedArtifact, File>()
            for (map in originalDepsFiles.values) {
                mods.putAll(map)
            }
            var prevNamespace = namespace
            var prevPrevNamespace = fallbackNamespace
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
                val namespaceKey = if (step.first) {
                    "${step.second}-${prevPrevNamespace}"
                } else {
                    "${step.second}-${prevNamespace}"
                }
                val forceReload = project.unimined.forceReload
                val targets = mods.mapValues {
                    it.value to (provider.mods as ModsProvider).modTransformFolder()
                        .resolve("${it.key.file.nameWithoutExtension}-mapped-${provider.mappings.combinedNames}-${namespaceKey}.${it.key.file.extension}")
                        .let { it to (it.exists() && !forceReload) }
                }
                project.logger.info("[Unimined/ModRemapper] Remapping Mods $step: ")
                if (targets.values.none { !it.second.second }) {
                    project.logger.info("[Unimined/ModRemapper]    Skipping remap step $step as all mods are already remapped")
                    mods.clear()
                    mods.putAll(targets.mapValues { it.value.second.first.toFile() })
                    prevNamespace = step.second
                    prevPrevNamespace = mcNamespace
                    continue
                }
                for (mod in targets) {
                    project.logger.info("[Unimined/ModRemapper]  ${if (mod.value.second.second) "skipping" else "        " } ${mod.value.first} -> ${mod.value.second.first}")
                }
                val remapper = constructRemapper(prevNamespace, step.second, mc)
                val tags = preRemapInternal(remapper.first, targets)
                mods.clear()
                mods.putAll(
                    remapInternal(
                        remapper,
                        tags.nonNullValues(),
                        prevNamespace to step.second
                    )
                )
                mods.putAll(
                    tags.filterValues { it == null }.mapValues { targets[it.key]!!.second.first.toFile() }
                )
                prevNamespace = step.second
                prevPrevNamespace = mcNamespace
            }
            // supply back to proper configs
            for (c in configurations) {
                val outConf = targetConfigurations[c]
                outConf!!.dependencies.clear()
                for (artifact in originalDepsFiles[c].keys) {
                    if (artifact.extension == "pom") continue
                    val classifier = artifact.classifier?.let { "$it-" } ?: ""
                    val output = "remapped_${artifact.moduleVersion.id.group}:${artifact.name}:${artifact.moduleVersion.id.version}:${classifier}mapped-${provider.mappings.combinedNames}-${provider.mappings.devNamespace}-${provider.mappings.devFallbackNamespace}@${artifact.extension ?: "jar"}"
                    outConf.dependencies.add(
                        project.dependencies.create(
                            output
                        )
                    )
                }
            }
        } else {
            for (c in configurations) {
                val outConf = targetConfigurations[c]
                outConf!!.dependencies.clear()
                outConf.dependencies.addAll(originalDeps[c])
            }
        }
    }

    private fun preRemapInternal(
        remapper: TinyRemapper,
        deps: Map<ResolvedArtifact, Pair<File, Pair<Path, Boolean>>>
    ): Map<ResolvedArtifact, Pair<InputTag, Pair<File, Path>>?> {
        val output = mutableMapOf<ResolvedArtifact, Pair<InputTag, Pair<File, Path>>?>()
        for ((artifact, data) in deps) {
            val file = data.first
            val target = data.second.first
            val needsRemap = !data.second.second
            project.logger.info("[Unimined/ModRemapper] remap ${needsRemap}; ${file} -> ${target}")
            if (file.isDirectory) {
                throw InvalidUserDataException("Cannot remap directory ${file.absolutePath}")
            } else {
                if (needsRemap) {
                    val tag = remapper.createInputTag()
                    remapper.readInputsAsync(tag, file.toPath())
                    output[artifact] = tag to (file to target)
                } else {
                    remapper.readClassPathAsync(file.toPath())
                    output[artifact] = null
                }
            }
        }
        return output
    }

    private fun remapInternal(
        remapper: Pair<TinyRemapper, BetterMixinExtension?>,
        deps: Map<ResolvedArtifact, Pair<InputTag, Pair<File, Path>>>,
        remap: Pair<MappingNamespace, MappingNamespace>
    ): Map<ResolvedArtifact, File> {
        val output = mutableMapOf<ResolvedArtifact, File>()
        project.logger.info("[Unimined/ModRemapper] Remapping mods to ${remap.first}/${remap.second}")
        for ((artifact, tag) in deps) {
            remapModInternal(remapper, artifact, tag, remap)
            output[artifact] = tag.second.first
        }
        remapper.first.finish()
        return output
    }

    private fun remapModInternal(
        remapper: Pair<TinyRemapper, BetterMixinExtension?>,
        dep: ResolvedArtifact,
        input: Pair<InputTag, Pair<File, Path>>,
        remap: Pair<MappingNamespace, MappingNamespace>,
    ) {
        if (remapper.second != null) {
            remapper.second!!.reset(dep.name + ".mixin-refmap.json")
        }
        val inpFile = input.second.first
        val targetFile = input.second.second
        project.logger.info("[Unimined/ModRemapper] Remapping mod from $inpFile -> $targetFile with mapping target ${remap.first}/${remap.second}")
        OutputConsumerPath.Builder(targetFile).build().use {
            it.addNonClassFiles(
                inpFile.toPath(),
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
            ZipReader.openZipFileSystem(targetFile, mapOf("mutable" to true)).use {
                remapper.second!!.write(it)
            }
        }
    }

    private val innerJarStripper: OutputConsumerPath.ResourceRemapper = object : OutputConsumerPath.ResourceRemapper {
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
