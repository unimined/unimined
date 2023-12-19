package xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit.buildtools

import com.google.gson.JsonParser
import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.util.forEachInZip
import xyz.wagyourtail.unimined.util.stream
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readText

class BuildToolsExecutor(
    private val project: Project,
    private val provider: MinecraftProvider,
    private val cache: Path
) {

    val buildInfo: BuildInfo by lazy {
        val buildInfoFile = cache.resolve("BuildInfo-${provider.version}.json")
        if (!buildInfoFile.exists() || project.unimined.forceReload) {
            URI.create("https://hub.spigotmc.org/versions/${provider.version}.json").stream()
                .use { input ->
                    buildInfoFile.outputStream().use {
                        input.copyTo(it)
                    }
                }
        }
        val buildInfoJson = JsonParser.parseString(buildInfoFile.readText())
        parseBuildInfo(buildInfoJson.asJsonObject)
    }

    val buildDataZip: Path by lazy {
        val buildDataFile = cache.resolve("BuildData-${provider.version}-${buildInfo.name}.zip")
        if (!buildDataFile.exists() || project.unimined.forceReload) {
            val version = buildInfo.refs.buildData
            URI.create("https://hub.spigotmc.org/stash/rest/api/latest/projects/SPIGOT/repos/builddata/archive?at=$version&format=zip").stream()
                .use { input ->
                    buildDataFile.outputStream().use {
                        input.copyTo(it)
                    }
                }
        }
        buildDataFile
    }

    val bukkitZip: Path by lazy {
        val bukkitFile = cache.resolve("Bukkit-${provider.version}-${buildInfo.name}.zip")
        if (!bukkitFile.exists() || project.unimined.forceReload) {
            val version = buildInfo.refs.bukkit
            URI.create("https://hub.spigotmc.org/stash/rest/api/latest/projects/SPIGOT/repos/bukkit/archive?at=$version&format=zip").stream()
                .use { input ->
                    bukkitFile.outputStream().use {
                        input.copyTo(it)
                    }
                }
        }
        bukkitFile
    }

    val craftBukkitZip: Path by lazy {
        val craftBukkitFile = cache.resolve("CraftBukkit-${provider.version}-${buildInfo.name}.zip")
        if (!craftBukkitFile.exists() || project.unimined.forceReload) {
            val version = buildInfo.refs.craftbukkit
            URI.create("https://hub.spigotmc.org/stash/rest/api/latest/projects/SPIGOT/repos/craftbukkit/archive?at=$version&format=zip").stream()
                .use { input ->
                    craftBukkitFile.outputStream().use {
                        input.copyTo(it)
                    }
                }
        }
        craftBukkitFile
    }

    val spigotZip: Path by lazy {
        val spigotFile = cache.resolve("Spigot-${provider.version}-${buildInfo.name}.zip")
        if (!spigotFile.exists() || project.unimined.forceReload) {
            val version = buildInfo.refs.spigot
            URI.create("https://hub.spigotmc.org/stash/rest/api/latest/projects/SPIGOT/repos/spigot/archive?at=$version&format=zip").stream()
                .use { input ->
                    spigotFile.outputStream().use {
                        input.copyTo(it)
                    }
                }
        }
        spigotFile
    }

    val buildDataFolder: Path by lazy {
        val buildDataFolder = cache.resolve("BuildData-${provider.version}-${buildInfo.name}")
        if (!buildDataFolder.exists() || project.unimined.forceReload) {
            buildDataFolder.createDirectories()
            buildDataZip.forEachInZip { s, inputStream ->
                buildDataFolder.resolve(s)
                    .apply {
                        parent?.createDirectories()
                        inputStream.copyTo(outputStream())
                    }
            }
        }
        buildDataFolder
    }

    val bukkitFolder: Path by lazy {
        val bukkitFolder = cache.resolve("Bukkit-${provider.version}-${buildInfo.name}")
        if (!bukkitFolder.exists() || project.unimined.forceReload) {
            bukkitFolder.createDirectories()
            bukkitZip.forEachInZip { s, inputStream ->
                bukkitFolder.resolve(s)
                    .apply {
                        parent?.createDirectories()
                        inputStream.copyTo(outputStream())
                    }
            }
        }
        bukkitFolder
    }

    val craftBukkitFolder: Path by lazy {
        val craftBukkitFolder = cache.resolve("CraftBukkit-${provider.version}-${buildInfo.name}")
        if (!craftBukkitFolder.exists() || project.unimined.forceReload) {
            craftBukkitFolder.createDirectories()
            craftBukkitZip.forEachInZip { s, inputStream ->
                craftBukkitFolder.resolve(s)
                    .apply {
                        parent?.createDirectories()
                        inputStream.copyTo(outputStream())
                    }
            }
        }
        craftBukkitFolder
    }

    val spigotFolder: Path by lazy {
        val spigotFolder = cache.resolve("Spigot-${provider.version}-${buildInfo.name}")
        if (!spigotFolder.exists() || project.unimined.forceReload) {
            spigotFolder.createDirectories()
            spigotZip.forEachInZip { s, inputStream ->
                spigotFolder.resolve(s)
                    .apply {
                        parent?.createDirectories()
                        inputStream.copyTo(outputStream())
                    }
            }
        }
        spigotFolder
    }

    val buildDataInfo by lazy {
        val buildDataInfoFile = buildDataFolder.resolve("buildData/info.json")
        JsonParser.parseString(buildDataInfoFile.readText()).asJsonObject
    }

    enum class Steps {
        EXTRACT,
        DECOMPILE,


    }

}