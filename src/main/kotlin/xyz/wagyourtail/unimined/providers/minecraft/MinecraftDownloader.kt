package xyz.wagyourtail.unimined.providers.minecraft

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.maybeCreate
import xyz.wagyourtail.unimined.providers.minecraft.version.*
import xyz.wagyourtail.unimined.testSha1
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.properties.Delegates

class MinecraftDownloader(val project: Project, val parent: MinecraftProvider) {
    private val METADATA_URL: URI = URI.create("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")

    private val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
    var client by Delegates.notNull<Boolean>()
    var server by Delegates.notNull<Boolean>()

    init {
        project.afterEvaluate {
            afterEvaluate()
        }
    }

    private fun afterEvaluate() {
        client = parent.disableCombined.get() || sourceSets.findByName("client") != null
        server = parent.disableCombined.get() || sourceSets.findByName("server") != null

        if (client) {
            parent.client.dependencies.add(
                project.dependencies.create(
                    "net.minecraft:minecraft:${version}:client"
                )
            )
        }
        if (server) {
            parent.server.dependencies.add(
                project.dependencies.create(
                    "net.minecraft:minecraft:${version}:server"
                )
            )
        }

        if (parent.disableCombined.get()) {
            parent.combined.let {
                it.dependencies.clear()
                if (client) {
                    it.dependencies.add(
                        project.dependencies.create(
                            "net.minecraft:minecraft:${version}:client"
                        )
                    )
                }
                if (server) {
                    it.dependencies.add(
                        project.dependencies.create(
                            "net.minecraft:minecraft:${version}:server"
                        )
                    )
                }
            }
        }
    }

    val version: String by lazy {
        val dependencies = parent.combined.dependencies

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

        dependency.version!!
    }

    val metadata: VersionData by lazy {
        val version = version
        val path = versionJsonDownloadPath(version)
        if (path.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            return@lazy parseVersionData(
                InputStreamReader(path.inputStream()).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            )
        } else {
            val versionIndex = getVersionFromLauncherMeta(version)
            val downloadPath = versionJsonDownloadPath(version)
            downloadPath.parent.maybeCreate()

            if (project.gradle.startParameter.isOffline) {
                throw IllegalStateException("Cannot download version metadata while offline")
            }

            if (!downloadPath.exists() || !testSha1(-1, versionIndex.get("sha1").asString, downloadPath)) {
                val url = versionIndex.get("url").asString
                val urlConnection = URI.create(url).toURL().openConnection() as HttpURLConnection
                urlConnection.setRequestProperty("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
                urlConnection.requestMethod = "GET"
                urlConnection.connect()

                if (urlConnection.responseCode != 200) {
                    throw IOException("Failed to get version data, ${urlConnection.responseCode}: ${urlConnection.responseMessage}")
                }

                urlConnection.inputStream.use {
                    Files.write(downloadPath, it.readBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                }

            }

            parseVersionData(
                InputStreamReader(path.inputStream()).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            )
        }
    }

    private fun getVersionFromLauncherMeta(versionId: String): JsonObject {
        if (project.gradle.startParameter.isOffline) {
            throw IllegalStateException("Offline mode is enabled, but version metadata is not available")
        }

        val urlConnection = METADATA_URL.toURL().openConnection() as HttpURLConnection
        urlConnection.setRequestProperty("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
        urlConnection.requestMethod = "GET"
        urlConnection.connect()

        if (urlConnection.responseCode != 200) {
            throw IOException("Failed to get metadata, ${urlConnection.responseCode}: ${urlConnection.responseMessage}")
        }

        val versionsList = urlConnection.inputStream.use {
            InputStreamReader(it).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
        }

        val versions = versionsList.getAsJsonArray("versions") ?: throw Exception("Failed to get metadata, no versions")
        for (version in versions) {
            val versionObject = version.asJsonObject
            val id = versionObject.get("id").asString
            if (id == versionId) {
                return versionObject
            }
        }
        throw IllegalStateException("Failed to get metadata, no version found for $versionId")
    }

    val minecraftClient: Path by lazy {
        val version = version

        val clientPath = clientJarDownloadPath(version)
        if (clientPath.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            clientPath
        } else {
            val metadata = metadata
            val clientJar = metadata.downloads["client"] ?: throw IllegalStateException("No client jar found for version $version")

            clientPath.parent.maybeCreate()
            download(clientJar, clientPath)
            clientPath
        }
    }

    val minecraftServer: Path by lazy {
        val version = version

        val serverPath = serverJarDownloadPath(version)
        if (serverPath.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            serverPath
        } else {
            val metadata = metadata
            val serverJar = metadata.downloads["server"] ?: throw IllegalStateException("No server jar found for version $version")

            serverPath.parent.maybeCreate()
            download(serverJar, serverPath)
            serverPath
        }
    }

    val minecraftCombined: Path by lazy {
        //TODO: actually combine the two
        minecraftClient
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

    private fun download(artifact: Artifact, path: Path) {

        if (testSha1(artifact.size, artifact.sha1 ?: "", path)) {
            return
        }

        artifact.url?.toURL()?.openStream()?.use {
            Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING)
        }

        if (!testSha1(artifact.size, artifact.sha1 ?: "", path)) {
            throw Exception("Failed to download " + artifact.url)
        }
    }

    private fun download(url: URI, path: Path) {
        url.toURL().openStream().use {
            Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun extract(dependency: Dependency, extract: Extract, path: Path) {
        val resolved = parent.mcLibraries.resolvedConfiguration
        resolved.getFiles { it == dependency }.forEach { file ->
            ZipInputStream(file.inputStream()).use { stream ->
                var entry = stream.nextEntry
                while (entry != null) {
                    if (entry.isDirectory) {
                        entry = stream.nextEntry
                        continue
                    }
                    if (extract.exclude.any { entry.name.startsWith(it) }) {
                        entry = stream.nextEntry
                        continue
                    }
                    Files.copy(stream, path.resolve(entry.name), StandardCopyOption.REPLACE_EXISTING)
                    entry = stream.nextEntry
                }
            }
        }
    }

    fun mcVersionFolder(version: String): Path {
        return parent.parent.getGlobalCache()
            .resolve("net")
            .resolve("minecraft")
            .resolve("minecraft")
            .resolve(version)
    }

    fun clientJarDownloadPath(version: String): Path {
        return mcVersionFolder(version).resolve("client.jar")
    }

    fun serverJarDownloadPath(version: String): Path {
        return mcVersionFolder(version).resolve("server.jar")
    }

    fun combinedPomPath(version: String): Path {
        return mcVersionFolder(version).resolve("minecraft-$version.xml")
    }

    fun combinedJarDownloadPath(version: String): Path {
        return mcVersionFolder(version).resolve("minecraft-$version.jar")
    }

    fun versionJsonDownloadPath(version: String): Path {
        return mcVersionFolder(version).resolve("version.json")
    }
}