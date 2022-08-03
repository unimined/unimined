package xyz.wagyourtail.unimined.providers.minecraft

import net.minecraftforge.artifactural.api.artifact.Artifact
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import net.minecraftforge.artifactural.api.artifact.ArtifactType
import net.minecraftforge.artifactural.api.repository.ArtifactProvider
import net.minecraftforge.artifactural.api.repository.Repository
import net.minecraftforge.artifactural.base.artifact.StreamableArtifact
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder
import net.minecraftforge.artifactural.base.repository.SimpleRepository
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.Constants

class MinecraftProvider(val project: Project) : ArtifactProvider<ArtifactIdentifier> {
    companion object MinecraftProviderStatic {
        private val minecraftProvidersByProject = mutableMapOf<Project, MinecraftProvider>()

        fun getMinecraftProvider(project: Project): MinecraftProvider {
            return minecraftProvidersByProject.computeIfAbsent(project) {
                MinecraftProvider(project)
            }
        }
    }

    val combined: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_COMBINED_PROVIDER)
    val client: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_CLIENT_PROVIDER)
    val server: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_SERVER_PROVIDER)
    val mcLibraries: Configuration = project.configurations.maybeCreate(Constants.MINECRAFT_LIBRARIES_PROVIDER)

    init {
        project.afterEvaluate {
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            sourceSets.findByName("main")?.let {
                it.compileClasspath += mcLibraries + combined
                it.runtimeClasspath += mcLibraries + combined
            }
            sourceSets.findByName("client")?.let {
                it.compileClasspath += mcLibraries + client
                it.runtimeClasspath += mcLibraries + client
            }
            sourceSets.findByName("server")?.let {
                it.compileClasspath += mcLibraries + server
                it.runtimeClasspath += mcLibraries + server
            }
        }

        project.afterEvaluate {
            MinecraftDownloader.downloadMinecraft(project)
        }
    }

    val repo: Repository = SimpleRepository.of(
        ArtifactProviderBuilder.begin(ArtifactIdentifier::class.java)
            .filter(ArtifactIdentifier.groupEquals(Constants.MINECRAFT_GROUP))
            .provide(this)
    )

    override fun getArtifact(info: ArtifactIdentifier): Artifact? {
        return try {
                StreamableArtifact.ofFile(
                    info, ArtifactType.BINARY, MinecraftDownloader.getMinecraft(project, info).toFile()
                )
        } catch (ignored: IllegalArgumentException) {
            null
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Failed to get artifact $info", e)
        }
    }
}