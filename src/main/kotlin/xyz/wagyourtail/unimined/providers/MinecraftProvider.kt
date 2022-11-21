package xyz.wagyourtail.unimined.providers

import groovy.lang.Closure
import groovy.lang.DelegatesTo
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
import xyz.wagyourtail.unimined.UniminedExtension
import xyz.wagyourtail.unimined.providers.minecraft.AssetsDownloader
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftDownloader
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.MinecraftJar
import xyz.wagyourtail.unimined.providers.patch.NoTransformMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.fabric.FabricMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.forge.ForgeMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.remap.MinecraftRemapper
import xyz.wagyourtail.unimined.providers.version.Extract
import xyz.wagyourtail.unimined.providers.version.Library
import xyz.wagyourtail.unimined.util.OSUtils
import xyz.wagyourtail.unimined.util.consumerApply
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
    val mcLibraries: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_LIBRARIES_PROVIDER).apply {
        isTransitive = true
    }

    val runConfigs = mutableListOf<RunConfig>()

    val minecraftDownloader: MinecraftDownloader = MinecraftDownloader(project, this)
    val assetsDownloader: AssetsDownloader = AssetsDownloader(
        project,
        this
    )

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

    @ApiStatus.Internal
    var minecraftTransformer: AbstractMinecraftTransformer = NoTransformMinecraftTransformer(project, this)

    init {
        overrideMainClassClient.convention(null as String?).finalizeValueOnRead()
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

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun fabric() {
        fabric {}
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun fabric(action: (FabricMinecraftTransformer) -> Unit) {
        if (minecraftTransformer !is NoTransformMinecraftTransformer) {
            throw IllegalStateException("Minecraft transformer already set")
        }
        minecraftTransformer = FabricMinecraftTransformer(project, this)
        action(minecraftTransformer as FabricMinecraftTransformer)
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun fabric(
        @DelegatesTo(
            value = FabricMinecraftTransformer::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        fabric {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun jarMod() {
        jarMod {}
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun jarMod(action: (JarModMinecraftTransformer) -> Unit) {
        if (minecraftTransformer !is NoTransformMinecraftTransformer) {
            throw IllegalStateException("Minecraft transformer already set")
        }
        minecraftTransformer = JarModMinecraftTransformer(project, this)
        action(minecraftTransformer as JarModMinecraftTransformer)
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun jarMod(
        @DelegatesTo(
            value = JarModMinecraftTransformer::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        jarMod {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun forge() {
        forge {}
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun forge(action: (ForgeMinecraftTransformer) -> Unit) {
        if (minecraftTransformer !is NoTransformMinecraftTransformer) {
            throw IllegalStateException("Minecraft transformer already set")
        }
        minecraftTransformer = ForgeMinecraftTransformer(project, this)
        action(minecraftTransformer as ForgeMinecraftTransformer)
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun forge(
        @DelegatesTo(
            value = ForgeMinecraftTransformer::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        forge {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    private fun sourceSets(sourceSets: SourceSetContainer) {
        val main = sourceSets.getByName("main")
        val client = sourceSets.findByName("client")
        val server = sourceSets.findByName("server")

        main.compileClasspath += mcLibraries
        main.runtimeClasspath += mcLibraries
        main.runtimeClasspath += project.files(main.output.resourcesDir)

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

    @ApiStatus.Internal
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
        project.logger.warn("Getting minecraft with mapping $envType:$namespace")
        return minecraftMapped.computeIfAbsent(envType) {
            mutableMapOf()
        }.computeIfAbsent(namespace) {
            val mc = if (envType == EnvType.COMBINED) {
                val client = MinecraftJar(
                    minecraftDownloader.getMinecraft(EnvType.CLIENT),
                    EnvType.CLIENT,
                    "official",
                    "official"
                )
                val server = MinecraftJar(
                    minecraftDownloader.getMinecraft(EnvType.SERVER),
                    EnvType.SERVER,
                    "official",
                    "official"
                )
                minecraftTransformer.merge(
                    client,
                    server,
                    minecraftDownloader.combinedJarDownloadPath(minecraftDownloader.version)
                )
            } else MinecraftJar(minecraftDownloader.getMinecraft(envType), envType, "official", "official")
            minecraftTransformer.afterRemap(
                envType,
                namespace,
                mcRemapper.provide(minecraftTransformer.transform(mc), namespace)
            )
        }
    }

    @ApiStatus.Internal
    override fun getArtifact(info: ArtifactIdentifier): Artifact {

        if (info.group != Constants.MINECRAFT_GROUP && info.group != Constants.MINECRAFT_FORGE_GROUP) {
            return Artifact.none()
        }

        if (!(info.classifier?.endsWith("mappings") ?: false)) {
            if (info.group == Constants.MINECRAFT_GROUP && minecraftTransformer is ForgeMinecraftTransformer) {
                throw IllegalStateException("Minecraft transformer is set to forge, but trying to get minecraft artifact")
            }

            if (info.group == Constants.MINECRAFT_FORGE_GROUP && minecraftTransformer !is ForgeMinecraftTransformer) {
                throw IllegalStateException("Minecraft transformer is not set to forge, but trying to get forge artifact")
            }
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
    fun provideVanillaRunClientTask(tasks: TaskContainer, overrides: (RunConfig) -> Unit = { }) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        val nativeDir = clientWorkingDirectory.get().resolve("natives")

        val preRunClient = project.tasks.create("preRunClient", consumerApply {
            doLast {
                if (nativeDir.exists()) {
                    nativeDir.deleteRecursively()
                }
                nativeDir.mkdirs()
                extractDependencies.forEach { (dep, extract) ->
                    minecraftDownloader.extract(dep, extract, nativeDir.toPath())
                }
                minecraftDownloader.metadata.assetIndex?.let {
                    assetsDownloader.downloadAssets(project, it)
                }
            }
        })

        val infoFile = minecraftDownloader.mcVersionFolder(minecraftDownloader.version)
            .resolve("${minecraftDownloader.version}.info")
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

        val assetsDir = assetsDownloader.assetsDir()

        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val runConfig = RunConfig(
            project,
            "runClient",
            "Minecraft Client",
            sourceSets.getByName("main"),
            sourceSets.findByName("client") ?: sourceSets.getByName("main"),
            overrideMainClassClient.getOrElse(minecraftDownloader.metadata.mainClass)!!,
            minecraftDownloader.metadata.getGameArgs(
                "Dev",
                clientWorkingDirectory.get().toPath(),
                assetsDir
            ),
            (minecraftDownloader.metadata.getJVMArgs(
                clientWorkingDirectory.get().resolve("libraries").toPath(),
                nativeDir.toPath()
            ) + betacraftArgs).toMutableList(),
            clientWorkingDirectory.get(),
            mutableMapOf(),
            assetsDir,
            listOf(preRunClient)
        )

        overrides(runConfig)

        val task = runConfig.createGradleTask(tasks, "Unimined")
        task.doFirst {
            if (!JavaVersion.current().equals(minecraftDownloader.metadata.javaVersion)) {
                project.logger.error("Java version is ${JavaVersion.current()}, expected ${minecraftDownloader.metadata.javaVersion}, Minecraft may not launch properly")
            }
        }

        runConfigs.add(runConfig)
    }

    @ApiStatus.Internal
    fun provideVanillaRunServerTask(tasks: TaskContainer, overrides: (RunConfig) -> Unit = { }) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val runConfig = RunConfig(
            project,
            "runServer",
            "Minecraft Server",
            sourceSets.getByName("main"),
            sourceSets.findByName("server") ?: sourceSets.getByName("main"),
            overrideMainClassServer.getOrElse(minecraftDownloader.metadata.mainClass)!!, // TODO: get from meta-inf, this is wrong
            mutableListOf("nogui"),
            mutableListOf(),
            project.projectDir.resolve("run").resolve("server"),
            mutableMapOf(),
            project.projectDir.resolve("run").resolve("server").toPath()
        )

        overrides(runConfig)

        runConfig.createGradleTask(tasks, "Unimined")
        runConfigs.add(runConfig)
    }

}