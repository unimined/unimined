package xyz.wagyourtail.unimined.internal.minecraft

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.tasks.SourceSet
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.patch.MergedPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.*
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.internal.mapping.MappingsProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.NoTransformMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.LegacyFabricMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.OfficialFabricMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.QuiltMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.ForgeMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod.JarModAgentMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.resolver.AssetsDownloader
import xyz.wagyourtail.unimined.internal.minecraft.resolver.Extract
import xyz.wagyourtail.unimined.internal.minecraft.resolver.Library
import xyz.wagyourtail.unimined.internal.minecraft.resolver.MinecraftDownloader
import xyz.wagyourtail.unimined.internal.minecraft.task.GenSourcesTaskImpl
import xyz.wagyourtail.unimined.internal.mods.ModsProvider
import xyz.wagyourtail.unimined.internal.mods.task.RemapJarTaskImpl
import xyz.wagyourtail.unimined.internal.runs.RunsProvider
import xyz.wagyourtail.unimined.util.*
import java.io.File
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.*

class MinecraftProvider(project: Project, sourceSet: SourceSet) : MinecraftConfig(project, sourceSet) {
    override var mcPatcher: MinecraftPatcher by FinalizeOnRead(ChangeOnce(NoTransformMinecraftTransformer(project, this)))

    override val mappings = MappingsProvider(project, this)

    override val mods = ModsProvider(project, this)

    override val runs = RunsProvider(project, this)

    override val minecraftData = MinecraftDownloader(project, this)

    override val minecraftRemapper = MinecraftRemapper(project, this)

    val minecraft: Configuration = project.configurations.maybeCreate("minecraft".withSourceSet(sourceSet)).also {
        sourceSet.compileClasspath += it
        sourceSet.runtimeClasspath += it
    }

    override val minecraftLibraries: Configuration = project.configurations.maybeCreate("minecraftLibraries".withSourceSet(sourceSet)).also {
        minecraft.extendsFrom(it)
        it.setTransitive(false)
    }

    override fun remap(task: Task, name: String, action: RemapJarTask.() -> Unit) {
        val remapTask = project.tasks.register(name, RemapJarTaskImpl::class.java, this)
        remapTask.configure {
            if (task is Jar) {
                it.inputFile.value(task.archiveFile)
            }
            it.action()
            it.dependsOn(task)
        }
        project.tasks.getByName("build").dependsOn(remapTask)
    }

    private val minecraftFiles: Map<Pair<MappingNamespace, MappingNamespace>, Path> = defaultedMapOf {
        project.logger.info("[Unimined/Minecraft] Providing minecraft files for $it")
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
        mcPatcher = ForgeMinecraftTransformer(project, this).also(action)
    }

    override fun jarMod(action: (JarModAgentPatcher) -> Unit) {
        mcPatcher = JarModAgentMinecraftTransformer(project, this).also(action)
    }

    val minecraftDepName: String = project.path.replace(":", "_").let { projectPath ->
        "minecraft${if (projectPath == "_") "" else projectPath}${if (sourceSet.name == "main") "" else "+"+sourceSet.name}"
    }

    private val extractDependencies: MutableMap<Dependency, Extract> = mutableMapOf()

    fun addLibraries(libraries: List<Library>) {
        for (library in libraries) {
            if (library.rules.all { it.testRule() }) {
                project.logger.info("[Unimined/Minecraft] Added dependency ${library.name}")
                val native = library.natives[OSUtils.oSId]
                if (library.url != null || library.downloads?.artifact != null || native == null) {
                    val dep = project.dependencies.create(library.name)
                    minecraftLibraries.dependencies.add(dep)
                    library.extract?.let { extractDependencies[dep] = it }
                }
                if (native != null) {
                    project.logger.info("[Unimined/Minecraft] Added native dependency ${library.name}:$native")
                    val nativeDep = project.dependencies.create("${library.name}:$native")
                    minecraftLibraries.dependencies.add(nativeDep)
                    library.extract?.let { extractDependencies[nativeDep] = it }
                }
            }
        }
    }

    private fun clientRun() {
        project.logger.info("[Unimined/Minecraft] client config")
        runs.addTarget(provideVanillaRunClientTask("client", project.file("run/client")))
        runs.configFirst("client", (mcPatcher as AbstractMinecraftTransformer)::applyClientRunTransform)
    }

    private fun serverRun() {
        project.logger.info("[Unimined/Minecraft] server config")
        runs.addTarget(provideVanillaRunServerTask("server", project.file("run/server")))
        runs.configFirst("server", (mcPatcher as AbstractMinecraftTransformer)::applyServerRunTransform)
    }

    fun applyRunConfigs() {
        project.logger.lifecycle("[Unimined/Minecraft] Applying run configs")
        when (side) {
            EnvType.CLIENT -> {
                clientRun()
            }
            EnvType.SERVER -> {
                serverRun()
            }
            EnvType.COMBINED -> {
                clientRun()
                serverRun()
            }
            else -> {
            }
        }
    }

    fun apply() {
        project.logger.lifecycle("[Unimined/Minecraft] Applying minecraft config for $sourceSet")
        // ensure minecraft deps are clear
        if (minecraft.dependencies.isNotEmpty()) {
            project.logger.warn("[Unimined/Minecraft] $minecraft dependencies are not empty! clearing...")
            minecraft.dependencies.clear()
        }
        if (minecraftLibraries.dependencies.isNotEmpty()) {
            project.logger.warn("[Unimined/Minecraft] $minecraftLibraries dependencies are not empty! clearing...")
            minecraftLibraries.dependencies.clear()
        }

        if (minecraftRemapper.replaceJSRWithJetbrains) {
            // inject jetbrains annotations into minecraftLibraries
            minecraftLibraries.dependencies.add(project.dependencies.create("org.jetbrains:annotations:24.0.1"))
        }

        // add minecraft dep
        minecraft.dependencies.add(project.dependencies.create("net.minecraft:$minecraftDepName:$version" + if (side != EnvType.COMBINED) ":${side.classifier}" else ""))

        // add minecraft libraries
        addLibraries(minecraftData.metadata.libraries)

        // create remapjar task
        val task = project.tasks.findByName("jar".withSourceSet(sourceSet))
        if (task != null && task is Jar) {
            task.apply {
                archiveClassifier.set("dev")
            }
            remap(task)
        }

        // apply minecraft patcher changes
        project.logger.lifecycle("[Unimined/Minecraft] Applying ${mcPatcher.name()}")
        (mcPatcher as AbstractMinecraftTransformer).apply()

        // create run configs
        applyRunConfigs()
        (mcPatcher as AbstractMinecraftTransformer).applyExtraLaunches()

        // finalize run configs
        runs.apply()

        // add gen sources task
        project.tasks.register("genSources".withSourceSet(sourceSet), GenSourcesTaskImpl::class.java, this).configure(consumerApply {
            group = "unimined"
            description = "Generates sources for $sourceSet's minecraft jar"
        })
    }

    override val minecraftFileDev: File by lazy {
        project.logger.info("[Unimined/Minecraft] Providing minecraft dev file to $sourceSet")
        getMinecraft(mappings.devNamespace, mappings.devFallbackNamespace).toFile().also {
            project.logger.info("[Unimined/Minecraft] Provided minecraft dev file $it")
        }
    }

    override fun isMinecraftJar(path: Path) = minecraftFiles.values.any { it == path }



    @ApiStatus.Internal
    fun provideVanillaRunClientTask(name: String, workingDirectory: File): RunConfig {
        val nativeDir = workingDirectory.resolve("natives")

        val preRunClient = project.tasks.create("preRun${name.capitalized()}".withSourceSet(sourceSet), consumerApply {
            group = "unimined_internal"
            description = "Prepares the run configuration for $name by extracting natives and downloading assets"
            doLast {
                if (nativeDir.exists()) {
                    nativeDir.deleteRecursively()
                }
                nativeDir.mkdirs()
                extractDependencies.forEach { (dep, extract) ->
                    minecraftData.extract(dep, extract, nativeDir.toPath())
                }
                minecraftData.metadata.assetIndex?.let {
                    AssetsDownloader.downloadAssets(project, it)
                }
            }
        })

        val infoFile = minecraftData.mcVersionFolder
            .resolve("${version}.info")
        if (!infoFile.exists()) {
            if (!project.gradle.startParameter.isOffline) {
                //test if betacraft has our version on file
                val url = URI.create(
                    "http://files.betacraft.uk/launcher/assets/jsons/${
                        URLEncoder.encode(
                            minecraftData.metadata.id,
                            StandardCharsets.UTF_8.name()
                        )
                    }.info"
                )
                    .toURL()
                    .openConnection() as HttpURLConnection
                url.setRequestProperty("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
                url.requestMethod = "GET"
                url.connect()
                if (url.responseCode == 200) {
                    infoFile.writeBytes(
                        url.inputStream.readBytes(),
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE
                    )
                }
            }
        }

        val properties = Properties()
        val betacraftArgs = if (infoFile.exists()) {
            infoFile.inputStream().use { properties.load(it) }
            properties.getProperty("proxy-args")?.split(" ") ?: listOf()
        } else {
            listOf()
        }

        val assetsDir = AssetsDownloader.assetsDir(project)

        return RunConfig(
            project,
            name,
            "run${name.capitalized()}",
            "Minecraft Client",
            sourceSet,
            minecraftData.metadata.mainClass,
            minecraftData.metadata.getGameArgs(
                "Dev",
                workingDirectory.toPath(),
                assetsDir
            ),
            (minecraftData.metadata.getJVMArgs(
                workingDirectory.resolve("libraries").toPath(),
                nativeDir.toPath()
            ) + betacraftArgs).toMutableList(),
            workingDirectory,
            mutableMapOf(),
            mutableListOf(preRunClient)
        )
    }

    @ApiStatus.Internal
    fun provideVanillaRunServerTask(name: String, workingDirectory: File): RunConfig {
        var mainClass: String? = null
        ZipReader.openZipFileSystem(minecraftData.minecraftServer.path).use {
            val properties = Properties()
            val metainf = it.getPath("META-INF/MANIFEST.MF")
            if (metainf.exists()) {
                metainf.inputStream().use { properties.load(it) }
                mainClass = properties.getProperty("Main-Class")
            }
        }

        return RunConfig(
            project,
            name,
            "run${name.capitalized()}",
            "Minecraft Server",
            sourceSet,
            mainClass ?: throw IllegalStateException("Could not find main class for server"),
            mutableListOf("nogui"),
            mutableListOf(),
            workingDirectory,
            mutableMapOf()
        )
    }
}