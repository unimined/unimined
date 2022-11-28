package xyz.wagyourtail.unimined.providers.patch.fabric

import com.google.gson.*
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.providers.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.MinecraftJar
import xyz.wagyourtail.unimined.remap.RemapJarTask
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeText

class FabricMinecraftTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(
    project,
    provider
) {
    companion object {
        val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    val fabric: Configuration = project.configurations.maybeCreate(Constants.FABRIC_PROVIDER)
    val fabricJson: Configuration = project.configurations.maybeCreate(Constants.FABRIC_JSON)

    val include: Configuration = project.configurations.maybeCreate(Constants.INCLUDE_PROVIDER)

    var accessWidener: File? = null
    var clientMainClass: String? = null
    var serverMainClass: String? = null

    init {
        project.repositories.maven {
            it.url = URI.create("https://maven.fabricmc.net")
        }
        FabricApiExtension.apply(project)
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

        val json = InputStreamReader(
            fabricJson.files(fabricJson.dependencies.last())
                .last()
                .inputStream()
        ).use { reader ->
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
        super.afterEvaluate()
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

    override fun transform(minecraft: MinecraftJar): MinecraftJar = minecraft

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar =
        if (accessWidener != null) {
            val output = MinecraftJar(
                baseMinecraft,
                parentPath = provider.parent.getLocalCache().resolve("fabric")
            )
            if (!output.path.exists() || project.gradle.startParameter.isRefreshDependencies) {
                if (AccessWidenerMinecraftTransformer.transform(
                    accessWidener!!.toPath(),
                    baseMinecraft.mappingNamespace,
                    baseMinecraft.path,
                    output.path,
                    false
                )) {
                    output
                } else {
                    baseMinecraft
                }
            } else {
                output
            }
        } else baseMinecraft

    private fun getIntermediaryClassPath(envType: EnvType): String {
        val remapClasspath = provider.parent.getLocalCache().resolve("remapClasspath.txt")
        val s = provider.mcLibraries.files.joinToString(":") + ":" +
                provider.parent.modProvider.modRemapper.internalModRemapperConfiguration(envType).files.joinToString(":") + ":" +
                provider.getMinecraftWithMapping(envType, "intermediary")

        remapClasspath.writeText(s, options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        return remapClasspath.absolutePathString()
    }


    override fun applyClientRunConfig(tasks: TaskContainer) {
        provider.provideVanillaRunClientTask(tasks) { task ->
            clientMainClass?.let { task.mainClass = it }
            task.jvmArgs += listOf(
                "-Dfabric.development=true",
                "-Dfabric.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.CLIENT)}\""
            )
        }
    }

    override fun applyServerRunConfig(tasks: TaskContainer) {
        provider.provideVanillaRunServerTask(tasks) { task ->
            serverMainClass?.let { task.mainClass = it }
            task.jvmArgs += listOf(
                "-Dfabric.development=true",
                "-Dfabric.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.SERVER)}\""
            )
        }
    }

    override fun afterRemapJarTask(task: RemapJarTask, output: Path) {
        insertIncludes(output)
        insertAW(output)
    }

    private fun insertIncludes(output: Path) {
        ZipReader.openZipFileSystem(output, mapOf("mutable" to true)).use { fs ->
            val mod = fs.getPath("fabric.mod.json")
            if (!Files.exists(mod)) {
                throw IllegalStateException("fabric.mod.json not found in jar")
            }
            val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(mod))).asJsonObject
            var jars = json.get("jars")?.asJsonArray
            if (jars == null) {
                jars = JsonArray()
                json.add("jars", jars)
            }
            Files.createDirectories(fs.getPath("META-INF/jars/"))
            val includeCache = provider.parent.getLocalCache().resolve("includeCache")
            Files.createDirectories(includeCache)
            for (dep in include.dependencies) {
                val path = fs.getPath("META-INF/jars/${dep.name}-${dep.version}.jar")
                val cachePath = includeCache.resolve("${dep.name}-${dep.version}.jar")
                if (!Files.exists(cachePath)) {
                    Files.copy(
                        include.files(dep).first { it.extension == "jar" }.toPath(),
                        includeCache.resolve("${dep.name}-${dep.version}.jar"),
                        StandardCopyOption.REPLACE_EXISTING
                    )

                    ZipReader.openZipFileSystem(cachePath, mapOf("mutable" to true)).use { innerfs ->
                        val innermod = innerfs.getPath("fabric.mod.json")
                        if (!Files.exists(innermod)) {
                            val innerjson = JsonObject()
                            innerjson.addProperty("schemaVersion", 1)
                            var artifactString = ""
                            if (dep.group != null) {
                                artifactString += dep.group!!.replace(".", "_") + "_"
                            }
                            artifactString += dep.name

                            innerjson.addProperty("id", artifactString)
                            innerjson.addProperty("version", dep.version)
                            innerjson.addProperty("name", dep.name)
                            val custom = JsonObject()
                            custom.addProperty("fabric-loom:generated", true)
                            innerjson.add("custom", custom)
                            Files.write(
                                innermod,
                                innerjson.toString().toByteArray(),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING
                            )
                        }
                    }
                }

                Files.copy(cachePath, path, StandardCopyOption.REPLACE_EXISTING)

                jars.add(JsonObject().apply {
                    addProperty("file", "META-INF/jars/${dep.name}-${dep.version}.jar")
                })
                Files.write(mod, GSON.toJson(json).toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
            }
        }
    }

    private fun insertAW(output: Path) {
        if (accessWidener != null) {
            ZipReader.openZipFileSystem(output, mapOf("mutable" to true)).use { fs ->
                val mod = fs.getPath("fabric.mod.json")
                if (!Files.exists(mod)) {
                    throw IllegalStateException("fabric.mod.json not found in jar")
                }
                val aw = accessWidener!!.toPath()
                var parent = aw.parent
                while (!fs.getPath(parent.relativize(aw).toString()).exists()) {
                    parent = parent.parent
                    if (parent.relativize(aw).toString() == aw.toString()) {
                        throw IllegalStateException("Access widener not found in jar")
                    }
                }
                val awPath = fs.getPath(parent.relativize(aw).toString())
                val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(mod))).asJsonObject
                json.addProperty("accessWidener", awPath.toString())
                Files.write(mod, GSON.toJson(json).toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
            }
        }
    }
}