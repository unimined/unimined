package xyz.wagyourtail.unimined.internal.minecraft.resolver

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.getSha1
import xyz.wagyourtail.unimined.util.testSha1
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream

object AssetsDownloader {

    fun downloadAssets(project: Project, assets: AssetIndex): Path {
        val dir = assetsDir(project)
        val index = dir.resolve("indexes").resolve("${assets.id}.json")

        index.parent.createDirectories()

        updateIndex(project, assets, index)

        val assetsJson = index.inputStream().use {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }

        resolveAssets(project, assetsJson, dir)
        return dir
    }

    fun assetsDir(project: Project): Path {
        return project.unimined.getGlobalCache().resolve("assets")
    }

    private fun updateIndex(project: Project, assets: AssetIndex, index: Path) =
        downloadAsset(project, assets.url!!, assets.size, assets.sha1!!, index, "assets index")

    @Synchronized
    private fun resolveAssets(project: Project, assetsJson: JsonObject, dir: Path) {
        project.logger.lifecycle("[Unimined/AssetDownloader] Resolving assets...")
        val copyToResources = assetsJson.get("map_to_resources")?.asBoolean ?: false
        for (key in assetsJson.keySet()) {
            val keyDir = dir.resolve(key)
            val value = assetsJson.get(key)
            if (value is JsonObject) {
                val entries = value.entrySet()
                project.logger.lifecycle("[Unimined/AssetDownloader] Resolving $key (${entries.size} files)...")
                var downloaded = AtomicInteger(0)
                val timeStart = System.currentTimeMillis()
                entries.parallelStream().forEach { (key, value) ->
                    val size = value.asJsonObject.get("size").asLong
                    val hash = value.asJsonObject.get("hash").asString
                    val assetPath = keyDir.resolve(hash.substring(0, 2)).resolve(hash)
                    val assetUrl = URI.create("https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash")

                    downloadAsset(project, assetUrl, size, hash, assetPath, key)

                    if (copyToResources) {
                        val resourcePath = project.projectDir.resolve("run")
                            .resolve("client")
                            .resolve("resources")
                            .resolve(key)
                        resourcePath.parentFile.mkdirs()
                        assetPath.inputStream()
                            .use { Files.copy(it, resourcePath.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                    }

                    if (project.logger.isDebugEnabled) {
                        project.logger.debug("[Unimined/AssetDownloader] ${downloaded.addAndGet(1) * 100 / entries.size}% (${downloaded}/${entries.size})\n")
                    } else {
                        print("${downloaded.addAndGet(1) * 100 / entries.size}% (${downloaded}/${entries.size})\r")
                        System.out.flush()
                    }
                }
                val timeEnd = System.currentTimeMillis()
                project.logger.lifecycle("[Unimined/AssetDownloader] Finished resolving $key in ${timeEnd - timeStart}ms")
            }
        }
    }

    private fun downloadAsset(project: Project, uri: URI, size: Long, hash: String, path: Path, key: String) {
        var i = 0
        while (!testSha1(size, hash, path) && i < 3) {
            if (i != 0) {
                project.logger.warn("[Unimined/AssetDownloader] Failed to download asset $key : $uri")
            }
            i += 1
            try {
                project.logger.info("[Unimined/AssetDownloader] Downloading $key : $uri")
                path.parent.createDirectories()

                val urlConnection = uri.toURL().openConnection() as HttpURLConnection

                urlConnection.connectTimeout = 5000
                urlConnection.readTimeout = 5000

                urlConnection.addRequestProperty(
                    "User-Agent",
                    "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)"
                )
                urlConnection.addRequestProperty("Accept", "*/*")
                urlConnection.addRequestProperty("Accept-Encoding", "gzip")

                if (urlConnection.responseCode == 200) {
                    urlConnection.inputStream.use {
                        Files.copy(if ("gzip" == urlConnection.contentEncoding) GZIPInputStream(it) else it, path, StandardCopyOption.REPLACE_EXISTING)
                    }
                } else {
                    project.logger.error("[Unimined/AssetDownloader] Failed to download asset $key $uri error code: ${urlConnection.responseCode}")
                }
            } catch (e: Exception) {
                project.logger.error("[Unimined/AssetDownloader] Failed to download asset $key : $uri", e)
            }
        }

        if (i == 3 && !testSha1(size, hash, path)) {
            val expected_size = size
            val actual_size = Files.size(path)
            if (expected_size != actual_size) {
                throw IOException("Failed to download asset $key : $uri , expected size: $expected_size, actual size: $actual_size")
            }
            val expected_hash = hash
            val actual_hash = path.getSha1()
            if (expected_hash != actual_hash) {
                throw IOException("Failed to download asset $key : $uri , expected hash: $expected_hash, actual hash: $actual_hash")
            }
            throw IOException("Failed to download asset $key : $uri , unknown error.")
        }
    }
}