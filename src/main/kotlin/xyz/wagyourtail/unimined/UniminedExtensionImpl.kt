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
import org.gradle.api.tasks.SourceSet
import org.gradle.configurationcache.extensions.capitalized
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.util.decapitalized
import java.net.URI

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

                    if (info.group != "net.minecraft") {
                        return Artifact.none()
                    }

                    project.logger.info("[Unimined/ArtifactProvider] $info")

                    if (info.extension == "pom") {
                        return Artifact.none()
                    }

                    when (info.name) {
                        "client_mappings", "client-mappings", "mappings" -> {
                            val mc = minecrafts.values.first { it.version == info.version }
                            return StreamableArtifact.ofFile(info, ArtifactType.BINARY, mc.minecraftData.officialClientMappingsFile)
                        }
                        "server_mappings", "server-mappings" -> {
                            val mc = minecrafts.values.first { it.version == info.version }
                            return StreamableArtifact.ofFile(info, ArtifactType.BINARY, mc.minecraftData.officialServerMappingsFile)
                        }
                        else -> {
                            val sourceSet = depNameToSourceSet(info.name)
                            if ((info.name != "minecraft" && !getMinecraftDepNames().contains(info.name))) {
                                return Artifact.none()
                            }
                            when (info.classifier) {
                                "client", "server", null -> {
                                    project.logger.info("[Unimined/ArtifactProvider] providing ${info.classifier ?: "combined"} jar")
                                    return StreamableArtifact.ofFile(info, ArtifactType.BINARY, minecrafts[sourceSet]!!.minecraftFileDev)
                                }
                                "sources" -> {
                                    //TODO: reconsider
                                    // this is because we only want to generate sources sometimes currently,
                                    // maybe have a flag for sources tho... but this flag needs to be changeable by CI's
                                    Artifact.none()
                                }
                                else -> {
                                    throw IllegalArgumentException("unknown classifier ${info.classifier}")
                                }
                            }
                        }
                    }

                    throw IllegalArgumentException("unknown classifier ${info.classifier}")
                }

            })
    )

    override val modsRemapRepo = project.repositories.flatDir {
        it.dir(getLocalCache().resolve("modTransform").toFile())
        it.content {
            it.includeGroupByRegex("remapped_.+")
        }
    }

    init {
        project.repositories.maven {
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

    fun afterEvaluate() {
        //TODO: ensure minecrafts don't overlap
    }
}