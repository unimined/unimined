package xyz.wagyourtail.unimined.providers.patch.fabric

import com.google.gson.JsonParser
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.UniminedPlugin
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.remap.MinecraftRemapper
import xyz.wagyourtail.unimined.providers.patch.remap.ModRemapper
import java.io.File
import java.io.InputStreamReader
import java.net.URI

class FabricMinecraftTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(
    project,
    provider
) {

    private val remapper = MinecraftRemapper(project, provider)
    // from int (fallback obf) to named, this is so pre-1.13 forge mods that target obf remap properly (looking at you baritone)
    val modRemapper = ModRemapper(project, remapper, remapper.fallbackRemapTo, remapper.remapFrom, remapper.remapTo)

    val fabric: Configuration = project.configurations.getByName(Constants.FABRIC_PROVIDER)

    var clientMainClass: String? = null
    var serverMainClass: String? = null
    val fabricJson: Configuration = project.configurations.maybeCreate(Constants.FABRIC_JSON)

    init {
        project.repositories.maven {
            it.url = URI.create("https://maven.fabricmc.net")
        }
    }

    override fun init() {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val main = sourceSets.getByName("main")

        main.compileClasspath += fabric
        main.runtimeClasspath += fabric

        val client = !UniminedPlugin.getOptions(project).disableCombined.get() || sourceSets.findByName("client") != null
        val server = !UniminedPlugin.getOptions(project).disableCombined.get() || sourceSets.findByName("server") != null

        val dependencies = fabric.dependencies

        if (dependencies.isEmpty()) {
            throw IllegalStateException("No dependencies found for fabric provider")
        }

        if (dependencies.size > 1) {
            throw IllegalStateException("Multiple dependencies found for fabric provider")
        }

        val dependency = dependencies.first()
        var artifactString = ""
        if (dependency.group != null) {
            artifactString += dependency.group + ":"
        }
        artifactString += dependency.name
        if (dependency.version != null) {
            artifactString += ":" + dependency.version
        }
        artifactString += "@json"

        if (fabricJson.dependencies.isEmpty()) {
            fabricJson.dependencies.add(
                project.dependencies.create(
                    artifactString
                )
            )
        }

        val json = InputStreamReader(fabricJson.files(fabricJson.dependencies.last()).last().inputStream()).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }

        val libraries = json.get("libraries")?.asJsonObject
        if (libraries != null) {
            libraries.get("common")?.asJsonArray?.forEach {
                if (client) {
                    provider.mcLibraries.dependencies.add(
                        project.dependencies.create(
                            it.asJsonObject.get("name").asString
                        )
                    )
                }
                if (server) {
                    provider.mcLibraries.dependencies.add(
                        project.dependencies.create(
                            it.asJsonObject.get("name").asString
                        )
                    )
                }
            }
            if (client) {
                libraries.get("client")?.asJsonArray?.forEach {
                    provider.mcLibraries.dependencies.add(
                        project.dependencies.create(
                            it.asJsonObject.get("name").asString
                        )
                    )
                }
            }
            if (server) {
                libraries.get("server")?.asJsonArray?.forEach {
                    provider.mcLibraries.dependencies.add(
                        project.dependencies.create(
                            it.asJsonObject.get("name").asString
                        )
                    )
                }
            }
        }
        val mainClass = json.get("mainClass")?.asJsonObject
        if (client) {
            clientMainClass = mainClass?.get("client")?.asString
        }
        if (server) {
            serverMainClass = mainClass?.get("server")?.asString
        }
    }

    override fun transform(artifact: ArtifactIdentifier, file: File): File {
        return remapper.transform(artifact, file)
    }

    override fun applyClientRunConfig() {
        provider.provideRunClientTask { task ->
            clientMainClass?.let { task.mainClass.set(it) }
            task.jvmArgs = listOf("-Dfabric.development=true") + (task.jvmArgs ?: emptyList())
         }
    }

    override fun applyServerRunConfig() {
        provider.provideRunServerTask { task ->
            serverMainClass?.let { task.mainClass.set(it) }
            task.jvmArgs = listOf("-Dfabric.development=true") + (task.jvmArgs ?: emptyList())
        }
    }
}