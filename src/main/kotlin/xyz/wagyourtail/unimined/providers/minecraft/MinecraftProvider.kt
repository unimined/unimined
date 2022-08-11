package xyz.wagyourtail.unimined.providers.minecraft

import net.minecraftforge.artifactural.api.artifact.Artifact
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import net.minecraftforge.artifactural.api.artifact.ArtifactType
import net.minecraftforge.artifactural.api.repository.ArtifactProvider
import net.minecraftforge.artifactural.api.repository.Repository
import net.minecraftforge.artifactural.base.artifact.StreamableArtifact
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder
import net.minecraftforge.artifactural.base.repository.SimpleRepository
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.OSUtils
import xyz.wagyourtail.unimined.UniminedExtension
import xyz.wagyourtail.unimined.consumerApply
import xyz.wagyourtail.unimined.idea.isIdeaSync
import xyz.wagyourtail.unimined.providers.minecraft.version.Extract
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.NoTransformMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.fabric.FabricMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.remap.MinecraftRemapper
import xyz.wagyourtail.unimined.providers.patch.remap.ModRemapper
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.writeBytes

@Suppress("LeakingThis")
abstract class MinecraftProvider(
    val project: Project,
    val parent: UniminedExtension
) : ArtifactProvider<ArtifactIdentifier> {

    val combined: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_COMBINED_PROVIDER)
    val client: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_CLIENT_PROVIDER)
    val server: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_SERVER_PROVIDER)
    val mcLibraries: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_LIBRARIES_PROVIDER)

    val minecraftDownloader: MinecraftDownloader = MinecraftDownloader(project, this)
    val assetsDownloader: AssetsDownloader = AssetsDownloader(project, this)

    private val repo: Repository = SimpleRepository.of(
        ArtifactProviderBuilder.begin(ArtifactIdentifier::class.java)
            .filter(ArtifactIdentifier.groupEquals(Constants.MINECRAFT_GROUP))
            .provide(this)
    )

    val mcRemapper = MinecraftRemapper(project, this)
    val modRemapper = ModRemapper(project, mcRemapper)

    abstract val overrideMainClassClient: Property<String?>
    abstract val overrideMainClassServer: Property<String?>
    abstract val targetNamespace: Property<String>
    abstract val clientWorkingDirectory: Property<File>
    abstract val serverWorkingDirectory: Property<File>
    abstract val disableCombined: Property<Boolean>
    abstract val transformer: Property<String>

    private lateinit var minecraftTransformer: AbstractMinecraftTransformer

    init {
        overrideMainClassClient.convention(null as String?).finalizeValueOnRead()
        overrideMainClassServer.convention(null as String?).finalizeValueOnRead()
        targetNamespace.convention("named").finalizeValueOnRead()
        clientWorkingDirectory.convention(project.projectDir.resolve("run").resolve("client")).finalizeValueOnRead()
        serverWorkingDirectory.convention(project.projectDir.resolve("run").resolve("server")).finalizeValueOnRead()
        disableCombined.convention(false).finalizeValueOnRead()
        transformer.convention("none").finalizeValueOnRead()

        project.repositories.maven {
            it.url = URI.create(Constants.MINECRAFT_MAVEN)
        }

        GradleRepositoryAdapter.add(
            project.repositories,
            "minecraft-transformer",
            parent.getGlobalCache().toFile(),
            repo
        )
        parent.events.register(::afterEvaluate)
        parent.events.register(::sourceSets)
    }

    private fun afterEvaluate() {
        addMcLibraries()

        minecraftTransformer = when(transformer.get()) {
            "none", null -> NoTransformMinecraftTransformer(project, this)
            "fabric" -> FabricMinecraftTransformer(project, this)
            "jarMod" -> JarModMinecraftTransformer(project, this)
            else -> throw IllegalArgumentException("Unknown transformer: ${transformer.get()}")
        }
        minecraftTransformer.afterEvaluate()
        modRemapper.remap()
    }

    private fun sourceSets(sourceSets: SourceSetContainer) {
        val main = sourceSets.getByName("main")
        val client = sourceSets.findByName("client")
        val server = sourceSets.findByName("server")

        main.compileClasspath += mcLibraries
        main.runtimeClasspath += mcLibraries

        client?.let {
            it.compileClasspath += this.client + main.compileClasspath
            it.runtimeClasspath += this.client + main.runtimeClasspath
        }

        server?.let {
            it.compileClasspath += this.server + main.compileClasspath
            it.runtimeClasspath += this.server + main.runtimeClasspath
        }

        main.compileClasspath += combined
        main.runtimeClasspath += combined
    }

    private lateinit var extractDependencies: MutableMap<Dependency, Extract>

    private fun addMcLibraries() {
        extractDependencies = mutableMapOf()
        for (library in minecraftDownloader.metadata.libraries) {
            project.logger.debug("Added dependency ${library.name}")
            if (library.rules.all { it.testRule() }) {
                if (library.url != null || library.downloads?.artifact != null) {
                    val dep = project.dependencies.create(library.name)
                    mcLibraries.dependencies.add(dep)
                    library.extract?.let { extractDependencies[dep] = it }
                }
                val native = library.natives[OSUtils.oSId]
                if (native != null) {
                    project.logger.debug("Added native dependency ${library.name}:$native")
                    val nativeDep = project.dependencies.create("${library.name}:$native")
                    mcLibraries.dependencies.add(nativeDep)
                    library.extract?.let { extractDependencies[nativeDep] = it }
                }
            }
        }
    }

    private val minecraftClientMapped = mutableMapOf<String, Path>()
    private val minecraftServerMapped = mutableMapOf<String, Path>()
//    private val minecraftCombinedMapped = mutableMapOf<String, Path>()

    fun getMinecraftClientWithMapping(namespace: String): Path {
        return minecraftClientMapped.computeIfAbsent(namespace) {
            mcRemapper.provideClient(minecraftTransformer.transformClient(minecraftDownloader.minecraftClient), namespace)
        }
    }

    fun getMinecraftServerWithMapping(namespace: String): Path {
        return minecraftServerMapped.computeIfAbsent(namespace) {
            mcRemapper.provideServer(minecraftTransformer.transformServer(minecraftDownloader.minecraftServer), namespace)
        }
    }

    fun getMinecraftCombinedWithMapping(namespace: String): Path {
        return minecraftClientMapped.computeIfAbsent(namespace) {
            mcRemapper.provideClient(minecraftTransformer.transformCombined(minecraftDownloader.minecraftCombined), namespace)
        }
    }

    override fun getArtifact(info: ArtifactIdentifier): Artifact {

        if (info.group != Constants.MINECRAFT_GROUP) {
            return Artifact.none()
        }

        if (info.name != "minecraft") {
            return Artifact.none()
        }

        return if (info.extension != "jar") {
                Artifact.none()
            } else {
                when (info.classifier) {
                    "client" -> StreamableArtifact.ofFile(
                        info,
                        ArtifactType.BINARY,
                        getMinecraftClientWithMapping(targetNamespace.get()).toFile()
                    )

                    "server" -> StreamableArtifact.ofFile(
                        info,
                        ArtifactType.BINARY,
                        getMinecraftServerWithMapping(targetNamespace.get()).toFile()
                    )

                    "client-mappings" -> StreamableArtifact.ofFile(
                        info,
                        ArtifactType.BINARY,
                        minecraftDownloader.clientMappings.toFile()
                    )

                    "server-mappings" -> StreamableArtifact.ofFile(
                        info,
                        ArtifactType.BINARY,
                        minecraftDownloader.serverMappings.toFile()
                    )

                    null -> StreamableArtifact.ofFile(
                        info,
                        ArtifactType.BINARY,
                        getMinecraftCombinedWithMapping(targetNamespace.get()).toFile()
                    )

                    else -> throw IllegalArgumentException("Unknown classifier ${info.classifier}")
                }
            }
    }


    fun provideRunClientTask(tasks: TaskContainer, overrides: (JavaExec) -> Unit) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        val nativeDir = clientWorkingDirectory.get().resolve("natives")
        val preRun = project.tasks.create("preRunClient", consumerApply {
            doLast {
                if (nativeDir.exists()) {
                    nativeDir.deleteRecursively()
                }
                nativeDir.mkdirs()
                extractDependencies.forEach { (dep, extract) ->
                    minecraftDownloader.extract(dep, extract, nativeDir.toPath())
                }
            }
        })

        if (isIdeaSync()) {
            if (nativeDir.exists()) {
                nativeDir.deleteRecursively()
            }
            nativeDir.mkdirs()
            extractDependencies.forEach { (dep, extract) ->
                minecraftDownloader.extract(dep, extract, nativeDir.toPath())
            }
        }
        val infoFile = minecraftDownloader.mcVersionFolder(minecraftDownloader.version).resolve("${minecraftDownloader.version}.info")
        if (!infoFile.exists()) {
            if (!project.gradle.startParameter.isOffline) {
                //test if betacraft has our version on file
                val url = URI.create("http://files.betacraft.uk/launcher/assets/jsons/${minecraftDownloader.metadata.id}.info")
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

        val assetsDir = minecraftDownloader.metadata.assetIndex?.let {
            assetsDownloader.downloadAssets(project, it)
        }

        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val runConfig = RunConfig(
            project,
            "runClient",
            "Minecraft Client",
            sourceSets.findByName("client") ?: sourceSets.getByName("main"),
            overrideMainClassClient.getOrElse(minecraftDownloader.metadata.mainClass)!!,
            minecraftDownloader.metadata.getGameArgs(
                "Dev",
                clientWorkingDirectory.get().toPath(),
                assetsDir ?: clientWorkingDirectory.get().resolve("assets").toPath()
            ),
            (minecraftDownloader.metadata.getJVMArgs(clientWorkingDirectory.get().resolve("libraries").toPath(), nativeDir.toPath()) + betacraftArgs).toMutableList(),
            clientWorkingDirectory.get()
        )
        val task = runConfig.createGradleTask(tasks, "Unimined")
        task.doFirst {
            if (!JavaVersion.current().equals(minecraftDownloader.metadata.javaVersion)) {
                project.logger.error("Java version is ${JavaVersion.current()}, expected ${minecraftDownloader.metadata.javaVersion}, Minecraft may not launch properly")
            }
        }
        if (isIdeaSync()) {
            runConfig.createIdeaRunConfig(minecraftDownloader.metadata.javaVersion)
        }
    }

    fun provideRunServerTask(tasks: TaskContainer, overrides: (RunConfig) -> Unit) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val runConfig = RunConfig(
            project,
            "runServer",
            "Minecraft Server",
            sourceSets.findByName("server") ?: sourceSets.getByName("main"),
            overrideMainClassServer.getOrElse(minecraftDownloader.metadata.mainClass)!!, // TODO: get from meta-inf
            mutableListOf("nogui"),
            mutableListOf(),
            project.projectDir.resolve("run").resolve("server")
        )

        overrides(runConfig)

        runConfig.createGradleTask(tasks, "Unimined")
        if (isIdeaSync()) {
            runConfig.createIdeaRunConfig(minecraftDownloader.metadata.javaVersion)
        }
    }

    enum class EnvType {
        COMBINED,
        CLIENT,
        SERVER
    }
}