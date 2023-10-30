package xyz.wagyourtail.unimined.internal.mods

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.mixin.MixinRemapOptions
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.patch.ForgeLikePatcher
import xyz.wagyourtail.unimined.api.mod.ModRemapConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.aw.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.extension.MixinRemapExtension
import xyz.wagyourtail.unimined.util.*
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name

class ModRemapProvider(config: Set<Configuration>, val project: Project, val provider: MinecraftConfig) : ModRemapConfig(config) {

    override var namespace: MappingNamespaceTree.Namespace by FinalizeOnRead(LazyMutable { provider.mcPatcher.prodNamespace })

    override var fallbackNamespace: MappingNamespaceTree.Namespace by FinalizeOnRead(LazyMutable { provider.mcPatcher.prodNamespace })

    private var catchAWNs by FinalizeOnRead(false)

    override fun namespace(ns: String) {
        // kotlin reflection is weird
        val delegate: FinalizeOnRead<MappingNamespaceTree.Namespace> = ModRemapProvider::class.getField("namespace")!!.getDelegate(this) as FinalizeOnRead<MappingNamespaceTree.Namespace>
        delegate.setValueIntl(LazyMutable { provider.mappings.getNamespace(ns) })
    }

    override fun fallbackNamespace(ns: String) {
        // kotlin reflection is weird
        val delegate: FinalizeOnRead<MappingNamespaceTree.Namespace> = ModRemapProvider::class.getField("fallbackNamespace")!!.getDelegate(this) as FinalizeOnRead<MappingNamespaceTree.Namespace>
        delegate.setValueIntl(LazyMutable { provider.mappings.getNamespace(ns) })
    }

    override fun catchAWNamespaceAssertion() {
        catchAWNs = true
    }

    override var remapAtToLegacy: Boolean by FinalizeOnRead(LazyMutable { (provider.mcPatcher as? ForgeLikePatcher)?.remapAtToLegacy == true })
    override fun mixinRemap(action: MixinRemapOptions.() -> Unit) {
        mixinRemap = action
    }

    var mixinRemap: MixinRemapOptions.() -> Unit by FinalizeOnRead {}
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
                "group" to "org.quiltmc",
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
        fromNs: MappingNamespaceTree.Namespace,
        toNs: MappingNamespaceTree.Namespace,
        mc: Path
    ): CompletableFuture<Pair<TinyRemapper, MixinRemapExtension>> {
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
        val mixinExtension = MixinRemapExtension(
                project.gradle.startParameter.logLevel,
                allowImplicitWildcards = true
            )
        mixinExtension.enableBaseMixin()
        mixinRemap(mixinExtension)
        remapperB.extension(mixinExtension)

        tinyRemapSettings(remapperB)
        val remapper = remapperB.build()
        val future = mixinExtension.readClassPath(remapper,
                *(provider.minecraftLibraries.files.map { it.toPath() } + listOf(mc))
                    .toTypedArray()
            )
        return future.thenApply { remapper to mixinExtension }
    }

    override fun remapper(remapperBuilder: TinyRemapper.Builder.() -> Unit) {
        tinyRemapSettings = remapperBuilder
    }

    fun doRemap(
        devNamespace: MappingNamespaceTree.Namespace = provider.mappings.devNamespace,
        devFallbackNamespace: MappingNamespaceTree.Namespace = provider.mappings.devFallbackNamespace,
        targetConfigurations: Map<Configuration, Configuration> = defaultedMapOf { it }
    ) {
        val path = provider.mappings.getRemapPath(
            namespace,
            fallbackNamespace,
            devFallbackNamespace,
            devNamespace
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
            project.logger.info("[Unimined/ModRemapper] remapAtToLegacy: $remapAtToLegacy")
            project.logger.info("[Unimined/ModRemapper] mixinRemap: $mixinRemap")
            project.logger.info(
                "[Unimined/ModRemapper] Remap path $namespace -> ${
                    path.joinToString(" -> ")
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
                val mcFallbackNamespace = prevPrevNamespace
                val mc = provider.getMinecraft(
                    mcNamespace,
                    mcFallbackNamespace
                )
                val namespaceKey = "${step}-${prevNamespace}"
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
                    prevNamespace = step
                    prevPrevNamespace = mcNamespace
                    continue
                }
                for (mod in targets) {
                    project.logger.info("[Unimined/ModRemapper]  ${if (mod.value.second.second) "skipping" else "        " } ${mod.value.first} -> ${mod.value.second.first}")
                }
                val remapper = constructRemapper(prevNamespace, step, mc)
                val tags = preRemapInternal(remapper, targets)
                mods.clear()
                mods.putAll(
                    remapInternal(
                        remapper.join(),
                        tags.join().nonNullValues(),
                        prevNamespace to step
                    )
                )
                mods.putAll(
                    tags.join().filterValues { it == null }.mapValues { targets[it.key]!!.second.first.toFile() }
                )
                prevNamespace = step
                prevPrevNamespace = mcNamespace
            }
            // supply back to proper configs
            for (c in configurations) {
                val outConf = targetConfigurations[c]
                project.logger.info("[Unimined/ModRemapper] Supplying remapped mods to ${c.name}")
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
                project.logger.info("[Unimined/ModRemapper] Supplying original mods to ${c.name}")
                outConf!!.dependencies.clear()
                outConf.dependencies.addAll(originalDeps[c])
            }
        }
    }

    private fun preRemapInternal(
        remapper: CompletableFuture<Pair<TinyRemapper, MixinRemapExtension>>,
        deps: Map<ResolvedArtifact, Pair<File, Pair<Path, Boolean>>>
    ): CompletableFuture<Map<ResolvedArtifact, Pair<InputTag, Pair<File, Path>>?>> {
        val output = mutableMapOf<ResolvedArtifact, Pair<InputTag, Pair<File, Path>>?>()
        var future = remapper
        val futures = mutableListOf<CompletableFuture<*>>()
        for ((artifact, data) in deps) {
            val file = data.first
            val target = data.second.first
            val needsRemap = !data.second.second
            project.logger.info("[Unimined/ModRemapper] remap ${needsRemap}; $file -> $target")
            if (file.isDirectory) {
                throw InvalidUserDataException("Cannot remap directory ${file.absolutePath}")
            } else {
                futures += future.thenCompose {
                    if (needsRemap) {
                        val tag = it.first.createInputTag()
                        output[artifact] = tag to (file to target)
                        it.second.readInput(it.first, tag, file.toPath()).thenApply { it }
                    } else {
                        output[artifact] = null
                        it.second.readClassPath(it.first, file.toPath()).thenApply { it }
                    }
                }
            }
        }
        return CompletableFuture.allOf(*futures.toTypedArray()).thenApply { output }
    }

    private fun ResolvedArtifact.stringify() = "${this.moduleVersion.id.group}:${this.name}:${this.moduleVersion.id.version}${this.classifier?.let { ":$it" } ?: ""}${this.extension?.let { "@$it" } ?: ""}"

    private fun remapInternal(
        remapper: Pair<TinyRemapper, MixinRemapExtension>,
        deps: Map<ResolvedArtifact, Pair<InputTag, Pair<File, Path>>>,
        remap: Pair<MappingNamespaceTree.Namespace, MappingNamespaceTree.Namespace>
    ): Map<ResolvedArtifact, File> {
        val output = mutableMapOf<ResolvedArtifact, File>()
        project.logger.info("[Unimined/ModRemapper] Remapping mods to ${remap.first}/${remap.second}")
        for ((artifact, tag) in deps) {
            try {
                remapModInternal(remapper, artifact, tag, remap)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to remap ${artifact.stringify()} to ${remap.first}/${remap.second}", e)
            }
            output[artifact] = tag.second.second.toFile()
        }
        remapper.first.finish()
        return output
    }

    private fun remapModInternal(
        remapper: Pair<TinyRemapper, MixinRemapExtension>,
        dep: ResolvedArtifact,
        input: Pair<InputTag, Pair<File, Path>>,
        remap: Pair<MappingNamespaceTree.Namespace, MappingNamespaceTree.Namespace>,
    ) {
        val inpFile = input.second.first
        val targetFile = input.second.second
        val manifest = (JarFile(inpFile).use { it.manifest }?.mainAttributes?.getValue("FMLAT") as String?)?.split(" ") ?: emptyList()
        project.logger.info("[Unimined/ModRemapper] Remapping mod from $inpFile -> $targetFile with mapping target ${remap.first}/${remap.second}")
        OutputConsumerPath.Builder(targetFile).build().use {
            it.addNonClassFiles(
                inpFile.toPath(),
                remapper.first,
                listOf(
                    AccessWidenerMinecraftTransformer.AwRemapper(
                        if (remap.first.named) "named" else remap.first.name,
                        if (remap.second.named) "named" else remap.second.name,
                        catchAWNs,
                        project.logger
                    ),
                    innerJarStripper,
                    AccessTransformerMinecraftTransformer.AtRemapper(project.logger, remapAtToLegacy, manifest)
                ) + NonClassCopyMode.FIX_META_INF.remappers
            )
            remapper.first.apply(it, input.first)
        }

        targetFile.openZipFileSystem(mapOf("mutable" to true)).use {
            remapper.second.insertExtra(input.first, it)
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
