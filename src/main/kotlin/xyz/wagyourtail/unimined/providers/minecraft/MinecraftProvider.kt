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
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.*
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.NoTransformsTransformer
import xyz.wagyourtail.unimined.providers.patch.fabric.FabricMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.remap.MinecraftRemapper
import java.net.HttpURLConnection
import java.net.URI
import java.util.*

class MinecraftProvider private constructor(val project: Project) : ArtifactProvider<ArtifactIdentifier> {
    companion object MinecraftProviderStatic {
        private val minecraftProvidersByProject = mutableMapOf<Project, MinecraftProvider>()

        @Synchronized
        fun getMinecraftProvider(project: Project): MinecraftProvider {
            return minecraftProvidersByProject.computeIfAbsent(project) {
                MinecraftProvider(project)
            }
        }

        private val transformers = linkedMapOf<(project: Project) -> Boolean, (project: Project, provider: MinecraftProvider) -> AbstractMinecraftTransformer>()
        private val resolvedTransformer = mutableMapOf<Project, AbstractMinecraftTransformer>()
        init {
            transformers[{
                it.configurations.findByName("fabric") != null
            }] = { project, provider ->
                FabricMinecraftTransformer(project, provider)
            }
            transformers[{
                it.configurations.findByName("mappings") != null
            }]  = { project, provider ->
                MinecraftRemapper(project, provider)
            }
            transformers[{
                true
            }] = { project, provider ->
                NoTransformsTransformer(project, provider)
            }
        }

        @Synchronized
        fun getTransformer(project: Project): AbstractMinecraftTransformer {
            return resolvedTransformer.computeIfAbsent(project) {
                val config = project.extensions.getByType(UniminedExtension::class.java)
                transformers.entries.find { it.key(project) }?.value?.invoke(project, getMinecraftProvider(project))?.apply { config.patchSettings.get().execute(this) } ?: throw IllegalStateException("No transformer found for project $project")
            }
        }

        private val minecraftDownloaders = mutableMapOf<Project, MinecraftDownloader>()

        @Synchronized
        fun getMinecraftDownloader(project: Project): MinecraftDownloader {
            return minecraftDownloaders.computeIfAbsent(project) {
                MinecraftDownloader(getMinecraftProvider(project))
            }
        }
    }

    val combined: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_COMBINED_PROVIDER)
    val client: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_CLIENT_PROVIDER)
    val server: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_SERVER_PROVIDER)
    val mcLibraries: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_LIBRARIES_PROVIDER)

    private val config = project.extensions.getByType(UniminedExtension::class.java)

    val repo: Repository = SimpleRepository.of(
        ArtifactProviderBuilder.begin(ArtifactIdentifier::class.java)
            .filter(ArtifactIdentifier.groupEquals(Constants.MINECRAFT_GROUP))
            .provide(this)
    )

    val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

    init {
        GradleRepositoryAdapter.add(project.repositories, "minecraft-transformer", UniminedPlugin.getGlobalCache(project).toFile(), repo)
        project.afterEvaluate {
            getMinecraftDownloader(project).downloadMinecraft()
            val transformer = getTransformer(project)
            transformer.init()

            val main = sourceSets.getByName("main")

            main.compileClasspath += mcLibraries
            main.runtimeClasspath += mcLibraries

            sourceSets.findByName("client")?.let {
                it.compileClasspath += client + main.compileClasspath
                it.runtimeClasspath += client + main.runtimeClasspath
            }

            sourceSets.findByName("server")?.let {
                it.compileClasspath += server + main.compileClasspath
                it.runtimeClasspath += server + main.runtimeClasspath
            }

            main.compileClasspath += combined
            main.runtimeClasspath += combined

            transformer.applyRunConfigs()

            project.tasks.named("jar", Jar::class.java) {
                it.from(sourceSets.findByName("client")?.allSource ?: main)
            }
        }
    }

    fun findTransformer(): AbstractMinecraftTransformer {
        for (entry in transformers.entries) {
            if (entry.key(project)) {
                return entry.value(project, this)
            }
        }
        throw IllegalStateException("No transformer found for project $project")
    }

    override fun getArtifact(info: ArtifactIdentifier): Artifact {
        return try {
                StreamableArtifact.ofFile(
                    info, ArtifactType.BINARY, getTransformer(project).transform(info, getMinecraftDownloader(project).getMinecraft(info).toFile())
                )
        } catch (ignored: IllegalArgumentException) {
            Artifact.none()
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Failed to get artifact $info", e)
        }
    }

    fun provideRunClientTask(overrides: (JavaExec) -> Unit) {
        val mcD = getMinecraftDownloader(project)

        val nativeDir = mcD.clientWorkingDir.resolve("natives")
        if (nativeDir.exists()) {
            nativeDir.deleteRecursively()
        }
        val preRun = project.tasks.create("preRunClient", consumerApply {
            doLast {
                nativeDir.mkdirs()
                mcD.extractDependencies.forEach { (dep, extract) ->
                    mcD.extract(dep, extract, nativeDir.toPath())
                }
            }
        })

        //test if betacraft has our version on file
        val url = URI.create("http://files.betacraft.uk/launcher/assets/jsons/${mcD.versionData.id}.info").toURL().openConnection() as HttpURLConnection
        url.setRequestProperty("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
        url.requestMethod = "GET"
        url.connect()
        val properties = Properties()
        val betacraftArgs = if (url.responseCode == 200) {
            url.inputStream.use { properties.load(it) }
            properties.getProperty("proxy-args")?.split(" ") ?: listOf()
        } else {
            listOf()
        }

        project.tasks.create("runClient", JavaExec::class.java, consumerApply {
            group = "Unimined"
            description = "Runs Minecraft Client"
            mainClass.set(UniminedPlugin.getOptions(project).::overrideMainClassClient.getOrElse(mcD.versionData.mainClass))
            workingDir = mcD.clientWorkingDir
            workingDir.mkdirs()

            classpath = sourceSets.findByName("client")?.runtimeClasspath
                ?: sourceSets.getByName("main").runtimeClasspath

            jvmArgs = mcD.versionData.getJVMArgs(workingDir.resolve("libraries").toPath(), nativeDir.toPath()) + betacraftArgs


            val assetsDir = mcD.versionData.assetIndex?.let { AssetsDownloader.downloadAssets(project, it) }
            args = mcD.versionData.getGameArgs(
                "Dev",
                workingDir.toPath(),
                assetsDir ?: workingDir.resolve("assets").toPath()
            )

            overrides(this)

            dependsOn.add(preRun)

            doFirst {
                if (!JavaVersion.current().equals(mcD.versionData.javaVersion)) {
                    project.logger.error("Java version is ${JavaVersion.current()}, expected ${mcD.versionData.javaVersion}, Minecraft may not launch properly")
                }
            }
        })
    }

    fun provideRunServerTask(overrides: (JavaExec) -> Unit) {
        val mcD = getMinecraftDownloader(project)
        project.tasks.create("runServer", JavaExec::class.java, consumerApply {
            group = "Unimined"
            description = "Runs Minecraft Server"
            mainClass.set(UniminedPlugin.getOptions(project).overrideMainClassServer.getOrElse(mcD.versionData.mainClass)) // TODO: get from meta-inf
            workingDir = project.projectDir.resolve("run").resolve("server")
            workingDir.mkdirs()

            classpath = sourceSets.findByName("server")?.runtimeClasspath
                ?: sourceSets.getByName("main").runtimeClasspath

            args = listOf("--nogui")
            overrides(this)
        })
    }
}