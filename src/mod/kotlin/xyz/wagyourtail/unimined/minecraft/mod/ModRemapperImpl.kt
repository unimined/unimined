package xyz.wagyourtail.unimined.minecraft.mod

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.tinyremapper.*
import net.fabricmc.tinyremapper.OutputConsumerPath.ResourceRemapper
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.configurationcache.extensions.capitalized
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.mod.ModRemapper
import xyz.wagyourtail.unimined.minecraft.patch.fabric.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.getTempFilePath
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.name

class ModRemapperImpl(
    val project: Project,
    provider: ModProviderImpl
) : ModRemapper(provider) {

    private val mcProvider by lazy { provider.parent.minecraftProvider }
    private val mappings by lazy { provider.parent.mappingsProvider }

    override var tinyRemapperConf by LazyMutable { provider.parent.minecraftProvider.mcRemapper.tinyRemapperConf }

    private val combinedConfig = Configs(project, EnvType.COMBINED, this)
    private val clientConfig = Configs(project, EnvType.CLIENT, this)
    private val serverConfig = Configs(project, EnvType.SERVER, this)

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

    private fun constructRemapper(envType: EnvType, fromNs: MappingNamespace, toNs: MappingNamespace, mc: Path): TinyRemapper {
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
        tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        remapper.readClassPathAsync(
            *project.minecraft.mcLibraries.resolve().map { it.toPath() }.toTypedArray()
        )
        remapper.readClassPathAsync(mc)
        return remapper
    }

    fun remap(envType: EnvType) {
        val config = when (envType) {
            EnvType.COMBINED -> combinedConfig
            EnvType.CLIENT -> clientConfig
            EnvType.SERVER -> serverConfig
        }

        val prodNamespace = project.minecraft.mcPatcher.prodNamespace
        val devFallbackNamespace = project.minecraft.mcPatcher.devFallbackNamespace
        val devNamespace = project.minecraft.mcPatcher.devNamespace

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
            val tags = preRemapInternal(remapper, mods)
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

    private fun remapInternal(remapper: TinyRemapper, envType: EnvType, deps: Map<ResolvedArtifact, Pair<InputTag, File>>, final: Boolean, remap: Pair<MappingNamespace, MappingNamespace>): Map<ResolvedArtifact, File> {
        val output = mutableMapOf<ResolvedArtifact, File>()
        for ((artifact, tag) in deps) {
            output[artifact] = remapModInternal(remapper, envType, artifact, tag, final, remap).toFile()
        }
        remapper.finish()
        return output
    }

    private fun remapModInternal(remapper: TinyRemapper, envType: EnvType, dep: ResolvedArtifact, input: Pair<InputTag, File>, final: Boolean, remap: Pair<MappingNamespace, MappingNamespace>): Path {
        val combinedNames = mappings.getCombinedNames(envType)
        val target = if (final) {
            modTransformFolder().resolve("${dep.file.nameWithoutExtension}-mapped-${combinedNames}-${mcProvider.mcPatcher.devNamespace}.${dep.file.extension}")
        } else {
            getTempFilePath(dep.file.nameWithoutExtension, ".jar")
        }
        OutputConsumerPath.Builder(target).build().use {
            it.addNonClassFiles(
                input.second.toPath(),
                remapper,
                listOf(
                    AccessWidenerMinecraftTransformer.awRemapper(
                        remap.first.namespace,
                        remap.second.namespace
                    ), innerJarStripper
                ) + NonClassCopyMode.FIX_META_INF.remappers
            )
            remapper.apply(it, input.first)
        }
        return target
    }

    private fun modTransformFolder(): Path {
        return provider.parent.getLocalCache().resolve("modTransform").createDirectories()
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

    @Suppress("MemberVisibilityCanBePrivate")
    data class Configs(val project: Project, val envType: EnvType, val parent: ModRemapperImpl) {
        val configurations = mutableSetOf<Configuration>()
        private val envTypeName = envType.classifier?.capitalized() ?: ""

        private fun registerConfiguration(configuration: Configuration): Configuration {
            configurations += configuration
            return configuration
        }

        val modCompileOnly: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modCompileOnly$envTypeName")
                .apply {
                    extendsFrom(project.configurations.getByName("compileOnly"))
                    exclude(
                        mapOf(
                            "group" to "net.fabricmc",
                            "module" to "fabric-loader"
                        )
                    )
                })

        val modRuntimeOnly: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modRuntimeOnly$envTypeName")
                .apply {
                    extendsFrom(project.configurations.getByName("runtimeOnly"))
                    exclude(
                        mapOf(
                            "group" to "net.fabricmc",
                            "module" to "fabric-loader"
                        )
                    )
                })

        val localRuntime: Configuration = project.configurations.maybeCreate("localRuntime$envTypeName").apply {
            extendsFrom(project.configurations.getByName("runtimeOnly"))
            exclude(
                mapOf(
                    "group" to "net.fabricmc",
                    "module" to "fabric-loader"
                )
            )
        }

        val modLocalRuntime: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modLocalRuntime" + envTypeName)
                .apply {
                    extendsFrom(project.configurations.getByName("localRuntime"))
                    exclude(
                        mapOf(
                            "group" to "net.fabricmc",
                            "module" to "fabric-loader"
                        )
                    )
                })

        val modImplementation: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modImplementation" + envTypeName)
                .apply {
                    extendsFrom(project.configurations.getByName("implementation"))
                    exclude(
                        mapOf(
                            "group" to "net.fabricmc",
                            "module" to "fabric-loader"
                        )
                    )
                })


        init {
            parent.provider.parent.events.register(::sourceSets)
        }

        private fun sourceSets(sourceSets: SourceSetContainer) {
            when (envType) {
                EnvType.SERVER -> {
                    for (sourceSet in parent.provider.parent.minecraftProvider.serverSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                }

                EnvType.CLIENT -> {
                    for (sourceSet in parent.provider.parent.minecraftProvider.clientSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                }

                EnvType.COMBINED -> {
                    for (sourceSet in parent.provider.parent.minecraftProvider.combinedSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                    for (sourceSet in parent.provider.parent.minecraftProvider.serverSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                    for (sourceSet in parent.provider.parent.minecraftProvider.clientSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                }
            }
        }
    }
}
