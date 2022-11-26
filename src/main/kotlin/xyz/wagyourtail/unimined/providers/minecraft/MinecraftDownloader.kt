package xyz.wagyourtail.unimined.providers.minecraft

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.Constants.METADATA_URL
import xyz.wagyourtail.unimined.providers.MinecraftProvider
import xyz.wagyourtail.unimined.providers.version.Download
import xyz.wagyourtail.unimined.providers.version.Extract
import xyz.wagyourtail.unimined.providers.version.VersionData
import xyz.wagyourtail.unimined.providers.version.parseVersionData
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.testSha1
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.properties.Delegates

@Suppress("MemberVisibilityCanBePrivate")
class MinecraftDownloader(val project: Project, private val parent: MinecraftProvider) {

    companion object {
        fun download(download: Download, path: Path) {

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
    }

    var client by Delegates.notNull<Boolean>()
    var server by Delegates.notNull<Boolean>()

    private val sourceSets: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)

    fun afterEvaluate() {
        // if <=1.2.5 disable combined automagically
        if (mcVersionCompare(version, "1.3") < 0) {
            parent.disableCombined.set(true)
        }

        project.logger.warn("Disable combined: ${parent.disableCombined.get()}")

        client = !parent.disableCombined.get() || sourceSets.findByName("client") != null
        server = !parent.disableCombined.get() || sourceSets.findByName("server") != null

        if (client) {
            if (parent.disableCombined.get()) {
                project.logger.warn("selecting split-jar client for sourceset client")
                parent.client.dependencies.add(
                    project.dependencies.create(
                        "net.minecraft:${dependency.name}:${version}:client"
                    )
                )
            } else {
                project.logger.warn("selecting combined-jar for sourceset client")
                parent.client.dependencies.add(
                    project.dependencies.create(
                        "net.minecraft:${dependency.name}:${version}"
                    )
                )
            }
        }
        if (server) {
            if (parent.disableCombined.get()) {
                project.logger.warn("selecting split-jar server for sourceset server")
                parent.server.dependencies.add(
                    project.dependencies.create(
                        "net.minecraft:${dependency.name}:${version}:server"
                    )
                )
            } else {
                project.logger.warn("selecting combined-jar for sourceset server")
                parent.server.dependencies.add(
                    project.dependencies.create(
                        "net.minecraft:${dependency.name}:${version}"
                    )
                )
            }
        }

        if (parent.disableCombined.get()) {
            parent.combined.let {
                it.dependencies.clear()
                if (client) {
                    it.dependencies.add(
                        project.dependencies.create(
                            "net.minecraft:${dependency.name}:${version}:client"
                        )
                    )
                }
                if (server) {
                    it.dependencies.add(
                        project.dependencies.create(
                            "net.minecraft:${dependency.name}:${version}:server"
                        )
                    )
                }
            }
        }
    }


    var dependency: Dependency by LazyMutable {
        val dependencies = parent.combined.dependencies

        if (dependencies.isEmpty()) {
            throw IllegalStateException("No dependencies found for Minecraft")
        }

        if (dependencies.size > 1) {
            throw IllegalStateException("Multiple dependencies found for Minecraft")
        }

        val dependency = dependencies.first()

        if (dependency.group != Constants.MINECRAFT_GROUP) {
            throw IllegalStateException("Invalid dependency group for Minecraft, expected ${Constants.MINECRAFT_GROUP} but got ${dependency.group}")
        }

        if (dependency.name != "minecraft") {
            throw IllegalArgumentException("Dependency $dependency is not a Minecraft dependency")
        }

        dependency
    }

    val version: String by lazy {
        dependency.version!!
    }

    val launcherMeta by lazy {
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

        versionsList.getAsJsonArray("versions") ?: throw Exception("Failed to get metadata, no versions")
    }

    fun mcVersionCompare(vers1: String, vers2: String): Int {
        if (vers1 == vers2) return 0
        for (i in launcherMeta) {
            if (i.asJsonObject["id"].asString == vers1) {
                return 1
            }
            if (i.asJsonObject["id"].asString == vers2) {
                return -1
            }
        }
        throw Exception("Failed to compare versions, $vers1 and $vers2 are not valid versions")
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
            downloadPath.parent.createDirectories()

            if (project.gradle.startParameter.isOffline) {
                throw IllegalStateException("Cannot download version metadata while offline")
            }

            if (!downloadPath.exists() || !testSha1(-1, versionIndex.get("sha1").asString, downloadPath)) {
                val url = versionIndex.get("url").asString
                val urlConnection = URI.create(url).toURL().openConnection() as HttpURLConnection
                urlConnection.setRequestProperty(
                    "User-Agent",
                    "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)"
                )
                urlConnection.requestMethod = "GET"
                urlConnection.connect()

                if (urlConnection.responseCode != 200) {
                    throw IOException("Failed to get version data, ${urlConnection.responseCode}: ${urlConnection.responseMessage}")
                }

                urlConnection.inputStream.use {
                    Files.write(
                        downloadPath,
                        it.readBytes(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
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
        for (version in launcherMeta) {
            val versionObject = version.asJsonObject
            val id = versionObject.get("id").asString
            if (id == versionId) {
                return versionObject
            }
        }
        throw IllegalStateException("Failed to get metadata, no version found for $versionId")
    }

    private val minecraftClient: Path by lazy {
        val version = version

        val clientPath = clientJarDownloadPath(version)
        if (clientPath.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            clientPath
        } else {
            val metadata = metadata
            val clientJar = metadata.downloads["client"]
                ?: throw IllegalStateException("No client jar found for version $version")

            clientPath.parent.createDirectories()
            download(clientJar, clientPath)
            clientPath
        }
    }

    private val minecraftServer: Path by lazy {
        val version = version

        val serverPath = serverJarDownloadPath(version)
        if (serverPath.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            serverPath
        } else {
            val metadata = metadata
            var serverJar = metadata.downloads["server"]

            if (serverJar == null) {
                // attempt to get off betacraft
                val uriPart = if (version.startsWith("b")) "beta/${parent.alphaServerVersionOverride.get() ?: version}" else if (version.startsWith(
                        "a"
                    )
                ) "alpha/${
                    parent.alphaServerVersionOverride.get() ?: version.replaceFirst(
                        "1",
                        "0"
                    )
                }" else "release/$version/$version"
                serverJar = Download("", -1, URI.create("http://files.betacraft.uk/server-archive/$uriPart.jar"))
            }

            serverPath.parent.createDirectories()
            try {
                download(serverJar, serverPath)
            } catch (e: FileNotFoundException) {
                throw IllegalStateException("No server jar found for version $version", e)
            }
            serverPath
        }
    }

    private val clientMappings: Path by lazy {
        val mappings = metadata.downloads["client_mappings"]
            ?: throw IllegalStateException("No client mappings found for version $version")
        val mappingsPath = clientMappingsDownloadPath(version)

        if (mappingsPath.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            mappingsPath
        } else {
            mappingsPath.parent.createDirectories()
            download(mappings, mappingsPath)
            mappingsPath
        }
    }

    fun getMinecraft(envType: EnvType): Path {
        return when (envType) {
            EnvType.CLIENT -> minecraftClient
            EnvType.SERVER -> minecraftServer
            EnvType.COMBINED -> throw IllegalStateException("This should be handled at mcprovider by calling transformer merge")
        }
    }

    private val serverMappings: Path by lazy {
        val mappings = metadata.downloads["server_mappings"]
            ?: throw IllegalStateException("No server mappings found for version $version")
        val mappingsPath = serverMappingsDownloadPath(version)

        if (mappingsPath.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            mappingsPath
        } else {
            mappingsPath.parent.createDirectories()
            download(mappings, mappingsPath)
            mappingsPath
        }
    }

    fun getMappings(envType: EnvType): Path {
        return when (envType) {
            EnvType.CLIENT -> clientMappings
            EnvType.SERVER -> serverMappings
            EnvType.COMBINED -> clientMappings
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
                    if (extract.exclude.any { entry!!.name.startsWith(it) }) {
                        entry = stream.nextEntry
                        continue
                    }
                    path.resolve(entry.name).parent.createDirectories()
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
        return mcVersionFolder(version).resolve("minecraft-$version-client.jar")
    }

    fun serverJarDownloadPath(version: String): Path {
        val versionF = if (version.startsWith("a")) {
            parent.alphaServerVersionOverride.get() ?: version.replaceFirst("1", "0")
        } else version
        return mcVersionFolder(version).resolve("minecraft-$versionF-server.jar")
    }

    fun clientMappingsDownloadPath(version: String): Path {
        return mcVersionFolder(version).resolve("client_mappings.txt")
    }

    fun serverMappingsDownloadPath(version: String): Path {
        return mcVersionFolder(version).resolve("server_mappings.txt")
    }

    @Suppress("UNUSED")
    fun combinedJarDownloadPath(version: String): Path {
        return mcVersionFolder(version).resolve("minecraft-$version.jar")
    }

    fun versionJsonDownloadPath(version: String): Path {
        return mcVersionFolder(version).resolve("version.json")
    }
}