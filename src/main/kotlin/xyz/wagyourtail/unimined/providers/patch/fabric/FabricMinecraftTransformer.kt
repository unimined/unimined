package xyz.wagyourtail.unimined.providers.patch.fabric

import com.google.gson.JsonParser
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText

class FabricMinecraftTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(
    project,
    provider
) {
    val fabric: Configuration = project.configurations.getByName(Constants.FABRIC_PROVIDER)

    var clientMainClass: String? = null
    var serverMainClass: String? = null
    val fabricJson: Configuration = project.configurations.maybeCreate(Constants.FABRIC_JSON)

    init {
        project.repositories.maven {
            it.url = URI.create("https://maven.fabricmc.net")
        }
    }

    override fun afterEvaluate() {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val client = !provider.disableCombined.get() || sourceSets.findByName("client") != null
        val server = !provider.disableCombined.get() || sourceSets.findByName("server") != null

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

    override fun sourceSets(sourceSets: SourceSetContainer) {
        val main = sourceSets.getByName("main")

        main.compileClasspath += fabric
        main.runtimeClasspath += fabric

        if (provider.minecraftDownloader.client) {
            sourceSets.findByName("client")?.let {
                it.compileClasspath += fabric
                it.runtimeClasspath += fabric
            }
        }

        if (provider.minecraftDownloader.server) {
            sourceSets.findByName("server")?.let {
                it.compileClasspath += fabric
                it.runtimeClasspath += fabric
            }
        }

    }

    override fun transformClient(baseMinecraft: Path): Path {
        return baseMinecraft
    }

    override fun transformServer(baseMinecraft: Path): Path {
        return baseMinecraft
    }

    override fun transformCombined(baseMinecraft: Path): Path {
        return baseMinecraft
    }

    private fun getIntermediaryClassPath(): String {
        val remapClasspath = provider.parent.getLocalCache().resolve("remapClasspath.txt")
        val s = provider.mcLibraries.files.joinToString(":") + ":" + provider.modRemapper.internalModRemapperConfiguration.files.joinToString(":") + ":" + provider.getMinecraftCombinedWithMapping("intermediary")
        remapClasspath.writeText(s, options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        return remapClasspath.absolutePathString()
    }


    override fun applyClientRunConfig(tasks: TaskContainer) {
        provider.provideRunClientTask(tasks) { task ->
            clientMainClass?.let { task.mainClass.set(it) }
            task.jvmArgs = listOf("-Dfabric.development=true", "-Dfabric.remapClasspathFile=${getIntermediaryClassPath()}") + (task.jvmArgs ?: emptyList())
        }
    }

    override fun applyServerRunConfig(tasks: TaskContainer) {
        provider.provideRunServerTask(tasks) { task ->
            serverMainClass?.let { task.mainClass = it }
            task.jvmArgs += listOf("-Dfabric.development=true", "-Dfabric.remapClasspathFile=${getIntermediaryClassPath()}")
        }
    }
}