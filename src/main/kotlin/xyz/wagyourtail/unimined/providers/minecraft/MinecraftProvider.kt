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
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.OSUtils
import xyz.wagyourtail.unimined.UniminedExtension
import xyz.wagyourtail.unimined.consumerApply
import xyz.wagyourtail.unimined.idea.isIdeaSync
import xyz.wagyourtail.unimined.providers.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.NoTransformMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.fabric.FabricMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.forge.ForgeMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.remap.MinecraftRemapper
import xyz.wagyourtail.unimined.providers.minecraft.version.Extract
import xyz.wagyourtail.unimined.providers.minecraft.version.Library
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

    abstract val overrideMainClassClient: Property<String?>
    abstract val overrideMainClassServer: Property<String?>
    abstract val targetNamespace: Property<String>
    abstract val clientWorkingDirectory: Property<File>
    abstract val serverWorkingDirectory: Property<File>
    abstract val disableCombined: Property<Boolean>
    abstract val alphaServerVersionOverride: Property<String?>

    private var minecraftTransformer: AbstractMinecraftTransformer = NoTransformMinecraftTransformer(project, this)

    init {
        overrideMainClassClient.convention(null as String?).finalizeValueOnRead()
        overrideMainClassServer.convention(null as String?).finalizeValueOnRead()
        targetNamespace.convention("named").finalizeValueOnRead()
        clientWorkingDirectory.convention(project.projectDir.resolve("run").resolve("client")).finalizeValueOnRead()
        serverWorkingDirectory.convention(project.projectDir.resolve("run").resolve("server")).finalizeValueOnRead()
        disableCombined.convention(false).finalizeValueOnRead()
        alphaServerVersionOverride.convention(null as String?).finalizeValueOnRead()

        project.repositories.maven {
            it.url = URI.create(Constants.MINECRAFT_MAVEN)
        }

        GradleRepositoryAdapter.add(
            project.repositories,
            "minecraft-transformer",
            parent.getLocalCache().toFile(),
            repo
        )
        parent.events.register(::afterEvaluate)
        parent.events.register(::sourceSets)
    }

    private fun afterEvaluate() {
        addMcLibraries()
        minecraftTransformer.afterEvaluate()
    }

    fun fabric() {
        fabric {}
    }

    fun fabric(action: (FabricMinecraftTransformer) -> Unit) {
        if (minecraftTransformer !is NoTransformMinecraftTransformer) {
            throw IllegalStateException("Minecraft transformer already set")
        }
        minecraftTransformer = FabricMinecraftTransformer(project, this)
        action(minecraftTransformer as FabricMinecraftTransformer)
    }

    fun jarMod() {
        jarMod {}
    }

    fun jarMod(action: (JarModMinecraftTransformer) -> Unit) {
        if (minecraftTransformer !is NoTransformMinecraftTransformer) {
            throw IllegalStateException("Minecraft transformer already set")
        }
        minecraftTransformer = JarModMinecraftTransformer(project, this)
        action(minecraftTransformer as JarModMinecraftTransformer)
    }

    fun forge() {
        forge {}
    }

    fun forge(action: (ForgeMinecraftTransformer) -> Unit) {
        if (minecraftTransformer !is NoTransformMinecraftTransformer) {
            throw IllegalStateException("Minecraft transformer already set")
        }
        minecraftTransformer = ForgeMinecraftTransformer(project, this)
        action(minecraftTransformer as ForgeMinecraftTransformer)
    }

    private fun sourceSets(sourceSets: SourceSetContainer) {
        val main = sourceSets.getByName("main")
        val client = sourceSets.findByName("client")
        val server = sourceSets.findByName("server")

        main.compileClasspath += mcLibraries
        main.runtimeClasspath += mcLibraries

        client?.let {
            it.compileClasspath += this.client + main.compileClasspath + main.output
            it.runtimeClasspath += this.client + main.runtimeClasspath + main.output
        }

        server?.let {
            it.compileClasspath += this.server + main.compileClasspath + main.output
            it.runtimeClasspath += this.server + main.runtimeClasspath + main.output
        }

        main.compileClasspath += combined
        main.runtimeClasspath += combined
    }

    private var extractDependencies: MutableMap<Dependency, Extract> = mutableMapOf()

    private fun addMcLibraries() {
        addMcLibraries(minecraftDownloader.metadata.libraries)
    }

    fun addMcLibraries(libs: List<Library>) {
        for (library in libs) {
            if (library.rules.all { it.testRule() }) {
                project.logger.warn("Added dependency ${library.name}")
                val native = library.natives[OSUtils.oSId]
                if (library.url != null || library.downloads?.artifact != null || native == null) {
                    val dep = project.dependencies.create(library.name)
                    mcLibraries.dependencies.add(dep)
                    library.extract?.let { extractDependencies[dep] = it }
                }
                if (native != null) {
                    project.logger.debug("Added native dependency ${library.name}:$native")
                    val nativeDep = project.dependencies.create("${library.name}:$native")
                    mcLibraries.dependencies.add(nativeDep)
                    library.extract?.let { extractDependencies[nativeDep] = it }
                }
            }
        }
    }

    private val minecraftMapped = mutableMapOf<EnvType, MutableMap<String, Path>>()

    @ApiStatus.Internal
    fun getMinecraftWithMapping(envType: EnvType, namespace: String): Path {
        return minecraftMapped.computeIfAbsent(envType) {
            mutableMapOf()
        }.computeIfAbsent(namespace) {
//            if (envType == EnvType.COMBINED)
//                getMinecraftWithMapping(EnvType.CLIENT, namespace) //TODO: fix to actually merge
//            else
                minecraftTransformer.afterRemap(envType, namespace, mcRemapper.provide(envType, minecraftTransformer.transform(envType, minecraftDownloader.getMinecraft(envType)), namespace))
        }
    }

    @ApiStatus.Internal
    override fun getArtifact(info: ArtifactIdentifier): Artifact {

        if (info.group != Constants.MINECRAFT_GROUP) {
            return Artifact.none()
        }

        if (info.name != "minecraft") {
            return Artifact.none()
        }
        try {
            return if (info.extension != "jar") {
                Artifact.none()
            } else {
                when (info.classifier) {
                    "client" -> {
                        val mc = getMinecraftWithMapping(EnvType.CLIENT, targetNamespace.get())
                        project.logger.info("providing client minecraft jar at $mc")
                        StreamableArtifact.ofFile(
                            info,
                            ArtifactType.BINARY,
                            mc.toFile()
                        )
                    }

                    "server" -> {
                        val mc = getMinecraftWithMapping(EnvType.SERVER, targetNamespace.get())
                        project.logger.info("providing server minecraft jar at $mc")
                        StreamableArtifact.ofFile(
                            info,
                            ArtifactType.BINARY,
                            mc.toFile()
                        )
                    }

                    "client-mappings" -> StreamableArtifact.ofFile(
                        info,
                        ArtifactType.BINARY,
                        minecraftDownloader.getMappings(EnvType.CLIENT).toFile()
                    )

                    "server-mappings" -> StreamableArtifact.ofFile(
                        info,
                        ArtifactType.BINARY,
                        minecraftDownloader.getMappings(EnvType.SERVER).toFile()
                    )

                    null -> {
                        if (disableCombined.get()) {
                            Artifact.none()
                        } else {
                            val mc = getMinecraftWithMapping(EnvType.COMBINED, targetNamespace.get())
                            project.logger.info("providing combined minecraft jar at $mc")
                            StreamableArtifact.ofFile(
                                info,
                                ArtifactType.BINARY,
                                mc.toFile()
                            )
                        }
                    }

                    "sources" -> {
                        Artifact.none()
                    }

                    else -> throw IllegalArgumentException("Unknown classifier ${info.classifier}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @ApiStatus.Internal
    fun provideRunClientTask(tasks: TaskContainer, overrides: (RunConfig) -> Unit) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        val nativeDir = clientWorkingDirectory.get().resolve("natives")

        project.tasks.create("preRunClient", consumerApply {
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
            clientWorkingDirectory.get(),
            mutableMapOf()
        )

        overrides(runConfig)

        val task = runConfig.createGradleTask(tasks, "Unimined")
        task.doFirst {
            if (!JavaVersion.current().equals(minecraftDownloader.metadata.javaVersion)) {
                project.logger.error("Java version is ${JavaVersion.current()}, expected ${minecraftDownloader.metadata.javaVersion}, Minecraft may not launch properly")
            }
        }
        project.logger.warn("IDEA Sync is ${if (isIdeaSync()) "true" else "false"}")
        if (isIdeaSync()) {
            runConfig.createIdeaRunConfig(minecraftDownloader.metadata.javaVersion)
        }
    }

    @ApiStatus.Internal
    fun provideRunServerTask(tasks: TaskContainer, overrides: (RunConfig) -> Unit) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val runConfig = RunConfig(
            project,
            "runServer",
            "Minecraft Server",
            sourceSets.findByName("server") ?: sourceSets.getByName("main"),
            overrideMainClassServer.getOrElse(minecraftDownloader.metadata.mainClass)!!, // TODO: get from meta-inf, this is wrong
            mutableListOf("nogui"),
            mutableListOf(),
            project.projectDir.resolve("run").resolve("server"),
            mutableMapOf()
        )

        overrides(runConfig)

        runConfig.createGradleTask(tasks, "Unimined")
        if (isIdeaSync()) {
            runConfig.createIdeaRunConfig(minecraftDownloader.metadata.javaVersion)
        }
    }

}