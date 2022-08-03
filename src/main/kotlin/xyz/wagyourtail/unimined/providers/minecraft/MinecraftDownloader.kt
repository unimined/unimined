package xyz.wagyourtail.unimined.providers.minecraft

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.*
import xyz.wagyourtail.unimined.providers.minecraft.version.Download
import xyz.wagyourtail.unimined.providers.minecraft.version.parseVersionData
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream

object MinecraftDownloader {

    private val METADATA_URL: URI = URI.create("https://launchermeta.mojang.com/mc/game/version_manifest.json")

    private val client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    fun downloadMinecraft(project: Project) {
        project.logger.info("Downloading Minecraft...")
        val dependencies = MinecraftProvider.getMinecraftProvider(project).combined.dependencies

        if (dependencies.isEmpty()) {
            throw IllegalStateException("No dependencies found for Minecraft")
        }

        if (dependencies.size > 1) {
            throw IllegalStateException("Multiple dependencies found for Minecraft")
        }

        val dependency = dependencies.first()


        if (dependency.group != Constants.MINECRAFT_GROUP) {
            throw IllegalArgumentException("Dependency $dependency is not Minecraft")
        }

        if (dependency.name != "minecraft") {
            throw IllegalArgumentException("Dependency $dependency is not a Minecraft dependency")
        }

        if (project.gradle.startParameter.isRefreshDependencies) {
            TODO()
        }

        // get mc version metadata
        val metadata = getMetadata(project)
        val version = getVersion(dependency.version!!, metadata)
        val versionData = parseVersionData(getVersionData(version))

        // get mc version jars
        val clientJar = versionData.downloads["client"]
        val serverJar = versionData.downloads["server"]

        if (clientJar == null || serverJar == null) {
            throw IllegalArgumentException("Could not find client or server jar for Minecraft version")
        }

        // get download files
        val clientJarDownloadPath = clientJarDownloadPath(project, versionData.id)
        val serverJarDownloadPath = serverJarDownloadPath(project, versionData.id)
        val combinedPomPath = combinedPomPath(project, versionData.id)

        clientJarDownloadPath.parent.maybeCreate()
        serverJarDownloadPath.parent.maybeCreate()

        // initiate downloads
        download(clientJar, clientJarDownloadPath)
        download(serverJar, serverJarDownloadPath)

        // write pom
        Files.writeString(
            combinedPomPath,
            XMLBuilder("project").addStringOption(
                "xsi:schemaLocation", "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd"
            ).addStringOption("xmlns", "http://maven.apache.org/POM/4.0.0").append(
                XMLBuilder("modelVersion").append("4.0.0"),
                XMLBuilder("groupId").append("net.minecraft"),
                XMLBuilder("artifactId").append("minecraft"),
                XMLBuilder("version").append(versionData.id),
                XMLBuilder("packaging").append("jar"),
            ).toString(),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
        )

        project.repositories.maven {
            it.url = URI.create(Constants.MINECRAFT_MAVEN)
        }

        // populate mc dependency configuration
        for (library in versionData.libraries) {
            project.logger.debug("Added dependency ${library.name}")
            if (library.rules.all { it.testRule() }) {
                MinecraftProvider.getMinecraftProvider(project).mcLibraries.dependencies.add(
                    project.dependencies.create(
                        library.name
                    )
                )
                val native = library.natives[OSUtils.oSId]
                if (native != null) {
                    project.logger.debug("Added native dependency ${library.name}:$native")
                    MinecraftProvider.getMinecraftProvider(project).mcLibraries.dependencies.add(
                        project.dependencies.create(
                            "${library.name}:$native"
                        )
                    )
                }
            }
        }

        // add runClient task
        project.tasks.create("runClient", JavaExec::class.java, consumerApply {
            group = "Unimined"
            description = "Runs Minecraft Client"
            mainClass.set(versionData.mainClass)
            workingDir = project.projectDir.resolve("run").resolve("client")
            workingDir.mkdirs()

            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            classpath = sourceSets.findByName("client")?.runtimeClasspath ?: sourceSets.getByName("main").runtimeClasspath

            jvmArgs = versionData.getJVMArgs(workingDir.resolve("libraries").toPath())

            val assetsDir = versionData.assetIndex?.let { AssetsDownloader.downloadAssets(project, it) }
            args = versionData.getGameArgs("Dev", workingDir.toPath(), assetsDir ?: workingDir.resolve("assets").toPath())
        })
        // add runServer task
        project.tasks.create("runServer", JavaExec::class.java, consumerApply {
            group = "Unimined"
            description = "Runs Minecraft Server"
            mainClass.set(versionData.mainClass)
            workingDir = project.projectDir.resolve("run").resolve("server")
            workingDir.mkdirs()

            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            classpath = sourceSets.findByName("server")?.runtimeClasspath ?: sourceSets.getByName("main").runtimeClasspath

            args = listOf("--nogui")
        })

        // add minecraft client/server deps
        MinecraftProvider.getMinecraftProvider(project).server.dependencies.add(
            project.dependencies.create(
                "net.minecraft:minecraft:${versionData.id}:server"
            )
        )
        MinecraftProvider.getMinecraftProvider(project).client.dependencies.add(
            project.dependencies.create(
                "net.minecraft:minecraft:${versionData.id}:client"
            )
        )
    }

    fun getMinecraft(project: Project, dependency: ArtifactIdentifier): Path {

        if (dependency.group != Constants.MINECRAFT_GROUP) {
            throw IllegalArgumentException("Dependency $dependency is not Minecraft")
        }

        if (dependency.name != "minecraft") {
            throw IllegalArgumentException("Dependency $dependency is not a Minecraft dependency")
        }

        if (dependency.extension == "pom") {
            return combinedPomPath(project, dependency.version)
        } else if (dependency.extension == "jar") {
            //TODO: combine jars
            // v1.2.5+ are combined anyway so...
            if (dependency.classifier == null || dependency.classifier == "client") {
                return clientJarDownloadPath(project, dependency.version)
            } else if (dependency.classifier == "server") {
                return serverJarDownloadPath(project, dependency.version)
            } else {
                throw IllegalArgumentException("Unknown classifier ${dependency.classifier}")
            }
        }
        throw IllegalStateException("Unknown dependency extension ${dependency.extension}")
    }

    private fun getMetadata(project: Project): JsonObject {

        if (project.gradle.startParameter.isOffline) {
            TODO()
        }

        val request = HttpRequest.newBuilder()
            .uri(METADATA_URL)
            .header("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
            .GET()
            .build()

        val response = client.send(request, BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            throw Exception("Failed to get metadata, " + response.statusCode())
        }

        return response.body().use {
            InputStreamReader(it).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
        }
    }

    private fun getVersion(versionId: String, metadata: JsonObject): JsonObject {
        val versions = metadata.getAsJsonArray("versions") ?: throw Exception("Failed to get metadata, no versions")

        for (version in versions) {
            val versionObject = version.asJsonObject
            val id = versionObject.get("id").asString
            if (id == versionId) {
                return versionObject
            }
        }
        throw Exception("Failed to get metadata, no version found for $versionId")
    }

    private fun getVersionData(version: JsonObject): JsonObject {
        val url = version.get("url").asString
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
            .GET()
            .build()

        val response = client.send(request, BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            throw Exception("Failed to get version data, " + response.statusCode())
        }

        return response.body().use {
            InputStreamReader(it).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
        }
    }

    private fun download(download: Download, path: Path) {

        if (testSha1(download.size, download.sha1, path)) {
            return
        }

        download.url?.toURL()?.openStream()?.use {
            Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING)
        }

        if (!testSha1(download.size, download.sha1, path)) {
            throw Exception("Failed to download " + download.url)
        }
    }

    fun clientJarDownloadPath(project: Project, version: String): Path {
        return UniminedPlugin.getGlobalCache(project)
            .resolve("net")
            .resolve("minecraft")
            .resolve("minecraft")
            .resolve(version)
            .resolve("client.jar")
    }

    fun serverJarDownloadPath(project: Project, version: String): Path {
        return UniminedPlugin.getGlobalCache(project)
            .resolve("net")
            .resolve("minecraft")
            .resolve("minecraft")
            .resolve(version)
            .resolve("server.jar")
    }

    fun combinedPomPath(project: Project, version: String): Path {
        return UniminedPlugin.getGlobalCache(project)
            .resolve("net")
            .resolve("minecraft")
            .resolve("minecraft")
            .resolve(version)
            .resolve("minecraft-$version.xml")
    }

    fun combinedJarDownloadPath(project: Project, version: String): Path {
        return UniminedPlugin.getGlobalCache(project)
            .resolve("net")
            .resolve("minecraft")
            .resolve("minecraft")
            .resolve(version)
            .resolve("minecraft-$version.jar")
    }
}