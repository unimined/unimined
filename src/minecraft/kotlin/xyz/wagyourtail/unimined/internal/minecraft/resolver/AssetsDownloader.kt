package xyz.wagyourtail.unimined.internal.minecraft.resolver

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.cachingDownload
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger
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

    private fun updateIndex(project: Project, assets: AssetIndex, index: Path) {
        project.cachingDownload(
            assets.url!!,
            assets.size,
            assets.sha1!!,
            index,
            )
    }

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

                    project.cachingDownload(
                        assetUrl,
                        size,
                        hash,
                        assetPath,
                    )

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

}