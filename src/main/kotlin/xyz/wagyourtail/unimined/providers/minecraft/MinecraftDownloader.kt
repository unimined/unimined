package xyz.wagyourtail.unimined.providers.minecraft

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.*
import xyz.wagyourtail.unimined.providers.minecraft.version.Artifact
import xyz.wagyourtail.unimined.providers.minecraft.version.Download
import xyz.wagyourtail.unimined.providers.minecraft.version.Extract
import xyz.wagyourtail.unimined.providers.minecraft.version.parseVersionData
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.*
import java.util.zip.ZipInputStream

object MinecraftDownloader {

    private val METADATA_URL: URI = URI.create("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")

    fun downloadMinecraft(project: Project) {
        project.logger.info("Downloading Minecraft...")
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        val client = !UniminedPlugin.getOptions(project).disableCombined.get() || sourceSets.findByName("client") != null
        val server = !UniminedPlugin.getOptions(project).disableCombined.get() || sourceSets.findByName("server") != null

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

        // get download files
        val clientJarDownloadPath = clientJarDownloadPath(project, versionData.id)
        val serverJarDownloadPath = serverJarDownloadPath(project, versionData.id)
        val combinedPomPath = combinedPomPath(project, versionData.id)

        clientJarDownloadPath.parent.maybeCreate()
        serverJarDownloadPath.parent.maybeCreate()

        // initiate downloads
        if (client && clientJar != null) {
            download(clientJar, clientJarDownloadPath)
        } else if (client) {
            throw IllegalStateException("No client jar found for Minecraft")
        }
        if (server && serverJar != null) {
            download(serverJar, serverJarDownloadPath)
        } else if (server) {
            throw IllegalStateException("No server jar found for Minecraft")
        }

        // write pom
        Files.write(
            combinedPomPath,
            XMLBuilder("project").addStringOption(
                "xsi:schemaLocation", "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd"
            ).addStringOption("xmlns", "http://maven.apache.org/POM/4.0.0").append(
                XMLBuilder("modelVersion").append("4.0.0"),
                XMLBuilder("groupId").append("net.minecraft"),
                XMLBuilder("artifactId").append("minecraft"),
                XMLBuilder("version").append(versionData.id),
                XMLBuilder("packaging").append("jar"),
            ).toString().toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
        )

        project.repositories.maven {
            it.url = URI.create(Constants.MINECRAFT_MAVEN)
        }

        // populate mc dependency configuration
        if (client) {
            val clientWorkingDir = project.projectDir.resolve("run").resolve("client")

            val extractDependencies = mutableMapOf<Dependency, Extract>()

            for (library in versionData.libraries) {
                project.logger.debug("Added dependency ${library.name}")
                if (library.rules.all { it.testRule() }) {
                    if (library.url != null || library.downloads?.artifact != null) {
                        val dep = project.dependencies.create(library.name)
                        MinecraftProvider.getMinecraftProvider(project).mcLibraries.dependencies.add(dep)
                        library.extract?.let { extractDependencies[dep] = it }
                    }
                    val native = library.natives[OSUtils.oSId]
                    if (native != null) {
                        project.logger.debug("Added native dependency ${library.name}:$native")
                        val nativeDep = project.dependencies.create("${library.name}:$native")
                        MinecraftProvider.getMinecraftProvider(project).mcLibraries.dependencies.add(nativeDep)
                        library.extract?.let { extractDependencies[nativeDep] = it }
                    }
                }
            }

            val nativeDir = clientWorkingDir.resolve("natives")
            if (nativeDir.exists()) {
                nativeDir.deleteRecursively()
            }
            val preRun = project.tasks.create("preRunClient", consumerApply {
                doLast {
                    nativeDir.mkdirs()
                    extractDependencies.forEach { (dep, extract) ->
                        extract(project, dep, extract, nativeDir.toPath())
                    }
                }
            })

            //test if betacraft has our version on file
            val url = URI.create("http://files.betacraft.uk/launcher/assets/jsons/${versionData.id}.info").toURL().openConnection() as HttpURLConnection
            url.setRequestProperty("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
            url.requestMethod = "GET"
            url.connect()
            val properties = Properties()
            val betacraftArgs = if (url.responseCode == 200) {
                url.inputStream.use { properties.load(it) }
                properties.getProperty("proxy-args")?.split(" ") ?: listOf()
            } else {
                listOf()
            }

            project.tasks.create("runClient", JavaExec::class.java, consumerApply {
                group = "Unimined"
                description = "Runs Minecraft Client"
                mainClass.set(versionData.mainClass)
                workingDir = clientWorkingDir
                workingDir.mkdirs()

                classpath = sourceSets.findByName("client")?.runtimeClasspath
                            ?: sourceSets.getByName("main").runtimeClasspath

                jvmArgs = versionData.getJVMArgs(workingDir.resolve("libraries").toPath(), nativeDir.toPath()) + betacraftArgs


                val assetsDir = versionData.assetIndex?.let { AssetsDownloader.downloadAssets(project, it) }
                args = versionData.getGameArgs(
                    "Dev",
                    workingDir.toPath(),
                    assetsDir ?: workingDir.resolve("assets").toPath()
                )

                dependsOn.add(preRun)

                doFirst {
                    if (!JavaVersion.current().equals(versionData.javaVersion)) {
                        project.logger.error("Java version is ${JavaVersion.current()}, expected ${versionData.javaVersion}, Minecraft may not launch properly")
                    }
                }
            })
        }

        // add runServer task
        if (server) {
            project.tasks.create("runServer", JavaExec::class.java, consumerApply {
                group = "Unimined"
                description = "Runs Minecraft Server"
                mainClass.set(versionData.mainClass)
                workingDir = project.projectDir.resolve("run").resolve("server")
                workingDir.mkdirs()

                classpath = sourceSets.findByName("server")?.runtimeClasspath
                            ?: sourceSets.getByName("main").runtimeClasspath

                args = listOf("--nogui")
            })
        }

        // add minecraft client/server deps
        if (client) {
            MinecraftProvider.getMinecraftProvider(project).client.dependencies.add(
                project.dependencies.create(
                    "net.minecraft:minecraft:${versionData.id}:client"
                )
            )
        }

        if (server) {
            MinecraftProvider.getMinecraftProvider(project).server.dependencies.add(
                project.dependencies.create(
                    "net.minecraft:minecraft:${versionData.id}:server"
                )
            )
        }

        if (UniminedPlugin.getOptions(project).disableCombined.get()) {
            MinecraftProvider.getMinecraftProvider(project).combined.let {
                it.dependencies.clear()
                if (client) {
                    it.dependencies.add(
                        project.dependencies.create(
                            "net.minecraft:minecraft:${versionData.id}:client"
                        )
                    )
                }
                if (server) {
                    it.dependencies.add(
                        project.dependencies.create(
                            "net.minecraft:minecraft:${versionData.id}:server"
                        )
                    )
                }
            }
        }
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
            return if (dependency.classifier == null || dependency.classifier == "client") {
                clientJarDownloadPath(project, dependency.version)
            } else if (dependency.classifier == "server") {
                serverJarDownloadPath(project, dependency.version)
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

        val urlConnection = METADATA_URL.toURL().openConnection() as HttpURLConnection
        urlConnection.setRequestProperty("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
        urlConnection.requestMethod = "GET"
        urlConnection.connect()

        if (urlConnection.responseCode != 200) {
            throw Exception("Failed to get metadata, ${urlConnection.responseCode}: ${urlConnection.responseMessage}")
        }

        return urlConnection.inputStream.use {
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
        val urlConnection = URI.create(url).toURL().openConnection() as HttpURLConnection
        urlConnection.setRequestProperty("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
        urlConnection.requestMethod = "GET"
        urlConnection.connect()

        if (urlConnection.responseCode != 200) {
            throw Exception("Failed to get version data, ${urlConnection.responseCode}: ${urlConnection.responseMessage}")
        }

        return urlConnection.inputStream.use {
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

    private fun extract(project: Project, dependency: Dependency, extract: Extract, path: Path) {
        val resolved = MinecraftProvider.getMinecraftProvider(project).mcLibraries.resolvedConfiguration
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