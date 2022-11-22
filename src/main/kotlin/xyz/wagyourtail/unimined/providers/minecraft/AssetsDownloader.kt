package xyz.wagyourtail.unimined.providers.minecraft

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.Project
import xyz.wagyourtail.unimined.Constants.ASSET_BASE_URL
import xyz.wagyourtail.unimined.providers.MinecraftProvider
import xyz.wagyourtail.unimined.providers.version.AssetIndex
import xyz.wagyourtail.unimined.util.testSha1
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream

class AssetsDownloader(val project: Project, private val parent: MinecraftProvider) {

    fun downloadAssets(project: Project, assets: AssetIndex): Path {
        val dir = assetsDir()
        val index = dir.resolve("indexes").resolve("${assets.id}.json")

        index.parent.createDirectories()

        updateIndex(assets, index)

        val assetsJson = index.inputStream().use {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }

        resolveAssets(project, assetsJson, dir)
        return dir
    }

    fun assetsDir(): Path {
        return parent.parent.getGlobalCache().resolve("assets")
    }

    private fun updateIndex(assets: AssetIndex, index: Path) {
        if (testSha1(assets.size, assets.sha1!!, index)) {
            return
        }

        assets.url?.toURL()?.openStream()?.use {
            Files.copy(it, index, StandardCopyOption.REPLACE_EXISTING)
        }

        if (!testSha1(assets.size, assets.sha1, index)) {
            throw Exception("Failed to download " + assets.url)
        }
    }

    companion object {
        @Synchronized
        private fun resolveAssets(project: Project, assetsJson: JsonObject, dir: Path) {
            val copyToResources = assetsJson.get("map_to_resources")?.asBoolean ?: false
            for (key in assetsJson.keySet()) {
                val keyDir = dir.resolve(key)
                val value = assetsJson.get(key)
                if (value is JsonObject) {
                    value.entrySet().forEach { (key, value) ->
                        val size = value.asJsonObject.get("size").asLong
                        val hash = value.asJsonObject.get("hash").asString
                        val assetPath = keyDir.resolve(hash.substring(0, 2)).resolve(hash)

                        var i = 0
                        while (!testSha1(size, hash, assetPath) && i < 3) {
                            i += 1
                            val assetUrl = URI.create("$ASSET_BASE_URL${hash.substring(0, 2)}/$hash")
                            project.logger.info("Downloading $key : $assetUrl")
                            assetPath.parent.createDirectories()

                            val urlConnection = assetUrl.toURL().openConnection() as HttpURLConnection

                            urlConnection.connectTimeout = 5000
                            urlConnection.readTimeout = 5000

                            urlConnection.addRequestProperty(
                                "User-Agent",
                                "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)"
                            )
                            urlConnection.addRequestProperty("Accept", "*/*")
                            urlConnection.addRequestProperty("Accept-Encoding", "gzip, deflate")

                            if (urlConnection.responseCode == 200) {
                                urlConnection.inputStream.use {
                                    Files.copy(it, assetPath, StandardCopyOption.REPLACE_EXISTING)
                                }
                            } else {
                                project.logger.error("Failed to download asset $key $assetUrl error code: ${urlConnection.responseCode}")
                            }
                            if (!testSha1(size, hash, assetPath)) {
                                project.logger.warn("Failed to download asset $key : $assetUrl")
                            }
                        }

                        if (copyToResources) {
                            val resourcePath = project.projectDir.resolve("run")
                                .resolve("client")
                                .resolve("resources")
                                .resolve(key)
                            resourcePath.parentFile.mkdirs()
                            assetPath.inputStream()
                                .use { Files.copy(it, resourcePath.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                        }
                    }
                }
            }
        }
    }
}