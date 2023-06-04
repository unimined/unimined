package xyz.wagyourtail.unimined

import net.minecraftforge.artifactural.api.artifact.Artifact
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import net.minecraftforge.artifactural.api.artifact.ArtifactType
import net.minecraftforge.artifactural.api.repository.ArtifactProvider
import net.minecraftforge.artifactural.api.repository.Repository
import net.minecraftforge.artifactural.base.artifact.StreamableArtifact
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder
import net.minecraftforge.artifactural.base.repository.SimpleRepository
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.tasks.SourceSet
import org.gradle.configurationcache.extensions.capitalized
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.util.defaultedMapOf
import java.net.URI
import java.nio.file.Path

open class UniminedExtensionImpl(project: Project) : UniminedExtension(project) {

    val minecrafts = mutableMapOf<SourceSet, MinecraftProvider>()


    override fun minecraft(sourceSet: SourceSet, action: MinecraftConfig.() -> Unit): MinecraftConfig {
        if (minecrafts.containsKey(sourceSet)) {
            project.logger.warn("[Unimined] minecraft config for ${sourceSet.name} already exists")
            return minecrafts[sourceSet]!!
        }
        project.logger.info("[Unimined] registering minecraft config for ${sourceSet.name}")
        val mc = MinecraftProvider(project, sourceSet)
        minecrafts[sourceSet] = mc
        mc.action()
        mc.apply()
        return mc
    }

    private fun getMinecraftDepNames(): Set<String> = minecrafts.values.map { it.minecraftDepName }.toSet()

    private fun depNameToSourceSet(depName: String): SourceSet {
        val sourceSetName = depName.substringAfter("+", "main")
        return minecrafts.keys.find { it.name == sourceSetName } ?: throw IllegalArgumentException("no source set found for $sourceSetName")
    }

    private val repo: Repository = SimpleRepository.of(
        ArtifactProviderBuilder.begin(ArtifactIdentifier::class.java)
            .filter(
                ArtifactIdentifier.groupEquals("net.minecraft").and(
                    ArtifactIdentifier.extensionEquals("pom").negate()
                ),
            )
            .provide(object : ArtifactProvider<ArtifactIdentifier> {

                override fun getArtifact(info: ArtifactIdentifier): Artifact {

                    try {
                        if (info.group != "net.minecraft") {
                            return Artifact.none()
                        }

                        when (info.name) {
                            "client_mappings", "client-mappings", "mappings" -> {
                                if (info.extension == "pom") {
                                    return Artifact.none()
                                }
                                val mc = minecrafts.values.first { it.version == info.version }
                                project.logger.info("[Unimined/ArtifactProvider] providing client mappings")
                                return StreamableArtifact.ofFile(
                                    info,
                                    ArtifactType.BINARY,
                                    mc.minecraftData.officialClientMappingsFile
                                )
                            }

                            "server_mappings", "server-mappings" -> {
                                if (info.extension == "pom") {
                                    return Artifact.none()
                                }
                                val mc = minecrafts.values.first { it.version == info.version }
                                project.logger.info("[Unimined/ArtifactProvider] providing server mappings")
                                return StreamableArtifact.ofFile(
                                    info,
                                    ArtifactType.BINARY,
                                    mc.minecraftData.officialServerMappingsFile
                                )
                            }

                            else -> {
                                val sourceSet = depNameToSourceSet(info.name)
                                if (!getMinecraftDepNames().contains(info.name)) {
                                    project.logger.warn("[Unimined/ArtifactProvider] unknown minecraft dep ${info.name}")
                                    return Artifact.none()
                                }
                                when (info.classifier) {
                                    "client", "server", null -> {
                                        if (info.extension == "pom") {
                                            return Artifact.none()
                                        }
                                        project.logger.info("[Unimined/ArtifactProvider] providing ${info.classifier ?: "combined"} jar")
                                        return StreamableArtifact.ofFile(
                                            info,
                                            ArtifactType.BINARY,
                                            minecrafts[sourceSet]!!.minecraftFileDev
                                        )
                                    }

                                    "sources" -> {
                                        //TODO: reconsider
                                        // this is because we only want to generate sources sometimes currently,
                                        // maybe have a flag for sources tho... but this flag needs to be changeable by CI's
                                        return Artifact.none()
                                    }

                                    else -> {
                                        throw IllegalArgumentException("unknown classifier ${info.classifier}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        project.logger.error("[Unimined/ArtifactProvider] error providing artifact $info", e)
                        throw e
                    }
                }
            })
    )

    override val modsRemapRepo = project.repositories.flatDir {
        it.name = "modsRemap"
        it.dir(getLocalCache().resolve("modTransform").toFile())
        it.content {
            it.includeGroupByRegex("remapped_.+")
        }
    }

    val forgeMaven by lazy {
        project.repositories.maven {
            it.name = "forge"
            it.url = URI("https://maven.minecraftforge.net/")
            it.metadataSources {
                it.mavenPom()
                it.artifact()
            }
        }
    }

    override fun forgeMaven() {
        project.logger.info("[Unimined] adding forge maven: $forgeMaven")
    }

    val fabricMaven by lazy {
        project.repositories.maven {
            it.name = "fabric"
            it.url = URI.create("https://maven.fabricmc.net")
        }
    }

    override fun fabricMaven() {
        project.logger.info("[Unimined] adding fabric maven: $fabricMaven")
    }

    val legacyFabricMaven by lazy {
        project.repositories.maven {
            it.name = "legacyFabric"
            it.url = URI.create("https://repo.legacyfabric.net/repository/legacyfabric")
        }
    }

    override fun legacyFabricMaven() {
        project.logger.info("[Unimined] adding legacy fabric maven: $legacyFabricMaven")
    }

    val quiltMaven by lazy {
        project.repositories.maven {
            it.name = "quilt"
            it.url = URI.create("https://maven.quiltmc.org/repository/release")
        }
    }

    override fun quiltMaven() {
        project.logger.info("[Unimined] adding quilt maven: $quiltMaven")
    }

    val babricMaven by lazy {
        project.repositories.maven {
            it.name = "babric"
            it.url = URI.create("https://maven.glass-launcher.net/babric/")
        }
    }

    override fun babricMaven() {
        project.logger.info("[Unimined] adding babric maven: $babricMaven")
    }

    val wagYourMaven = defaultedMapOf<String, MavenArtifactRepository> { name ->
        project.repositories.maven {
            it.name = "WagYourTail (${name.capitalized()})"
            it.url = project.uri("https://maven.wagyourtail.xyz/$name/")
        }
    }

    override fun wagYourMaven(name: String) {
        project.logger.info("[Unimined] adding wagyourtail maven: ${wagYourMaven[name]}")
    }

    val mcphackersIvy by lazy {
        project.repositories.ivy { ivy ->
            ivy.name = "mcphackers"
            ivy.url = URI.create("https://mcphackers.github.io/versionsV2/")
            ivy.patternLayout {
                it.artifact("[revision].[ext]")
            }
            ivy.content {
                it.includeModule("io.github.mcphackers", "mcp")
            }
            ivy.metadataSources {
                it.artifact()
            }
        }
    }

    override fun mcphackersIvy() {
        project.logger.info("[Unimined] adding mcphackers ivy: $mcphackersIvy")
    }

    init {
        project.repositories.maven {
            it.name = "minecraft"
            it.url = URI.create("https://libraries.minecraft.net/")
        }
        GradleRepositoryAdapter.add(
            project.repositories,
            "minecraft-transformer",
            getLocalCache().resolve("synthetic-resource-provider").toFile(),
            repo
        )
        project.repositories.all { repo ->
            if (repo != modsRemapRepo) {
                repo.content {
                    it.excludeGroupByRegex("remapped_.+")
                }
            }
        }
        project.afterEvaluate {
            afterEvaluate()
        }
    }

    fun getSourceSetFromMinecraft(path: Path): SourceSet? {
        for ((set, mc) in minecrafts) {
            if (mc.isMinecraftJar(path)) {
                return set
            }
        }
        return null
    }

    fun afterEvaluate() {
        for (sourceSet in minecrafts.keys) {
            val mcFiles = sourceSet.runtimeClasspath.files.mapNotNull { getSourceSetFromMinecraft(it.toPath()) }
            if (mcFiles.size > 1) {
                throw IllegalStateException("multiple minecraft jars in runtime classpath of $sourceSet, from $mcFiles")
            }
        }
    }
}