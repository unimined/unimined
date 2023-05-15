package xyz.wagyourtail.unimined.internal.minecraft.resolver

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.resolver.MinecraftData
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.stream
import xyz.wagyourtail.unimined.util.testSha1
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

class MinecraftDownloader(val project: Project, val provider: MinecraftProvider) : MinecraftData() {

    val version
        get() = provider.version

    val mcVersionFolder: Path by lazy {
        project.unimined.getGlobalCache()
            .resolve("net")
            .resolve("minecraft")
            .resolve("minecraft")
            .resolve(version)
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

    val launcherMeta by lazy {
        if (project.gradle.startParameter.isOffline) {
            throw IllegalStateException("Offline mode is enabled, but version metadata is not available")
        }

        val urlConnection = URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json").openConnection() as HttpURLConnection
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

    private fun getVersionFromLauncherMeta(versionId: String): JsonObject {
        for (version in launcherMeta) {
            val versionObject = version.asJsonObject
            val id = versionObject.get("id").asString
            if (id == versionId.substringAfter("empty-")) {
                return versionObject
            }
        }
        throw IllegalStateException("Failed to get metadata, no version found for $versionId")
    }

    override var metadataURL: URI by FinalizeOnRead(LazyMutable {
        val versionIndex = getVersionFromLauncherMeta(version)
        val url = versionIndex.get("url").asString
        URI.create(url)
    })

    val metadata by lazy {
        val versionJson = mcVersionFolder.resolve("version.json")
        if (!versionJson.exists() || project.unimined.forceReload) {
            versionJson.parent.createDirectories()

            val url = metadataURL
            url.stream().use {
                Files.copy(it, versionJson, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        parseVersionData(
            versionJson.reader().use {
                JsonParser.parseReader(it).asJsonObject
            }
        )
    }

    override var isPreCombined: Boolean by FinalizeOnRead(LazyMutable {
        mcVersionCompare(version, "1.3") < 0
    })

    fun download(download: Download, path: Path) {

        if (testSha1(download.size, download.sha1, path)) {
            return
        }

        download.url?.stream()?.use {
            Files.copy(it, path, StandardCopyOption.REPLACE_EXISTING)
        }

        if (!testSha1(download.size, download.sha1, path)) {
            throw Exception("Failed to download " + download.url)
        }
    }

    private fun getServerVersionOverrides(): Map<String, String> {
        val versionOverrides = mutableMapOf<String, String>()
        val versionOverridesFile = project.unimined.getGlobalCache().resolve("server-version-overrides.json")

        if (!versionOverridesFile.exists()) {
            try {
                URI.create("https://maven.wagyourtail.xyz/releases/mc-c2s.json").stream().use {
                    Files.write(
                        versionOverridesFile,
                        it.readBytes(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
                }
            } catch (e: Exception) {
                versionOverridesFile.deleteIfExists()
                throw e
            }
        }

        // read file into json
        if (versionOverridesFile.exists()) {
            InputStreamReader(versionOverridesFile.inputStream()).use {
                val json = JsonParser.parseReader(it).asJsonObject
                for (entry in json.entrySet()) {
                    versionOverrides[entry.key] = entry.value.asString
                }
            }
        }

        return versionOverrides
    }

    val minecraftClient: MinecraftJar by lazy {
        val clientPath = mcVersionFolder.resolve("minecraft-$version-client.jar")
        if (!clientPath.exists() || project.unimined.forceReload) {
            clientPath.parent.createDirectories()
            if (version.startsWith("empty-")) {
                clientPath.outputStream().use {
                    ZipOutputStream(it).close()
                }
            } else {
                val clientJar = metadata.downloads["client"]
                    ?: throw IllegalStateException("No client jar found for version $version")
                download(clientJar, clientPath)
            }
        }
        MinecraftJar(
            mcVersionFolder,
            "minecraft",
            EnvType.CLIENT,
            version,
            listOf(),
            MappingNamespace.OFFICIAL,
            MappingNamespace.OFFICIAL,
            null,
            "jar",
            clientPath
        )
    }

    var serverVersionOverride: String by FinalizeOnRead(LazyMutable {
        getServerVersionOverrides().getOrDefault(version, version)
    })

    val minecraftServer: MinecraftJar by lazy {
        val serverPath = mcVersionFolder.resolve("minecraft-$version-server.jar")
        if (!serverPath.exists() || project.unimined.forceReload) {
            serverPath.parent.createDirectories()
            if (version.startsWith("empty-")) {
                serverPath.outputStream().use {
                    ZipOutputStream(it).close()
                }
            } else {
                var serverJar = metadata.downloads["server"]

                if (serverJar == null) {
                    // attempt to get off betacraft
                    val serverVersion = serverVersionOverride

                    val uriPart = if (version.startsWith("b")) {
                        "beta/${serverVersionOverride}"
                    } else if (version.startsWith("a")) {
                        "alpha/${
                            serverVersionOverride
                        }"
                    } else {
                        val folder = version.split(".").subList(0, 2).joinToString(".")
                        "release/$folder/${serverVersionOverride}"
                    }
                    serverJar = Download("", -1, URI.create("http://files.betacraft.uk/server-archive/$uriPart.jar"))
                }

                download(serverJar, serverPath)
            }
        }
        MinecraftJar(
            mcVersionFolder,
            "minecraft",
            EnvType.SERVER,
            version,
            listOf(),
            MappingNamespace.OFFICIAL,
            MappingNamespace.OFFICIAL,
            null,
            "jar",
            serverPath
        )
    }

    override val officialClientMappingsFile: File by lazy {
        val clientMappingsPath = mcVersionFolder.resolve("client_mappings.txt")
        if (!clientMappingsPath.exists() || project.unimined.forceReload) {
            clientMappingsPath.parent.createDirectories()
            if (version.startsWith("empty-")) {
                clientMappingsPath.outputStream().use {
                    ZipOutputStream(it).close()
                }
            } else {
                val clientMappings = metadata.downloads["client_mappings"]
                    ?: throw IllegalStateException("No client mappings found for version $version")
                download(clientMappings, clientMappingsPath)
            }
        }
        clientMappingsPath.toFile()
    }

    override val officialServerMappingsFile: File by lazy {
        val serverMappingsPath = mcVersionFolder.resolve("server_mappings.txt")
        if (!serverMappingsPath.exists() || project.unimined.forceReload) {
            serverMappingsPath.parent.createDirectories()
            if (version.startsWith("empty-")) {
                serverMappingsPath.outputStream().use {
                    ZipOutputStream(it).close()
                }
            } else {
                val serverMappings = metadata.downloads["server_mappings"]
                    ?: throw IllegalStateException("No server mappings found for version $version")
                download(serverMappings, serverMappingsPath)
            }
        }
        serverMappingsPath.toFile()
    }

    fun getMinecraft(envType: EnvType): MinecraftJar {
        return when (envType) {
            EnvType.CLIENT -> minecraftClient
            EnvType.SERVER -> minecraftServer
            EnvType.COMBINED -> throw IllegalStateException("This should be handled at mcprovider by calling transformer merge")
        }
    }

    fun getMappings(envType: EnvType): File {
        return when (envType) {
            EnvType.CLIENT -> officialClientMappingsFile
            EnvType.SERVER -> officialServerMappingsFile
            EnvType.COMBINED -> officialClientMappingsFile
        }
    }

    fun extract(dependency: Dependency, extract: Extract, path: Path) {
        val resolved = provider.minecraftLibraries.resolvedConfiguration
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

}