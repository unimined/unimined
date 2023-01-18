package xyz.wagyourtail.unimined.minecraft.patch.fabric

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.FabricLikePatcher
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.util.LazyMutable
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

abstract class FabricLikeMinecraftTransformer(
    project: Project,
    provider: MinecraftProviderImpl,
    val providerName: String,
    val modJsonName: String,
    val accessWidenerJsonKey: String
) : AbstractMinecraftTransformer(
    project,
    provider
), FabricLikePatcher {
    companion object {
        val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    val fabric: Configuration = project.configurations.maybeCreate(providerName)
    private val fabricJson: Configuration = project.configurations.detachedConfiguration()

    private val include: Configuration = project.configurations.maybeCreate(Constants.INCLUDE_PROVIDER)

    override var accessWidener: File? = null

    protected var clientMainClass: String? = null
    protected var serverMainClass: String? = null


    override val prodNamespace = MappingNamespace.INTERMEDIARY
    override var devNamespace by LazyMutable { MappingNamespace.findByType(MappingNamespace.Type.NAMED, project.mappings.getAvailableMappings(EnvType.COMBINED)) }
    override var devFallbackNamespace by LazyMutable { MappingNamespace.findByType(MappingNamespace.Type.INT, project.mappings.getAvailableMappings(EnvType.COMBINED)) }

    init {
        addMavens()
    }

    protected abstract fun addMavens()

    override fun afterEvaluate() {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val client = provider.clientSourceSets.isNotEmpty() || provider.combinedSourceSets.isNotEmpty()
        val server = provider.serverSourceSets.isNotEmpty() || provider.combinedSourceSets.isNotEmpty()

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

        for (sourceSet in provider.combinedSourceSets) {
            sourceSet.compileClasspath += fabric
            sourceSet.runtimeClasspath += fabric
        }

        for (sourceSet in provider.clientSourceSets) {
            sourceSet.compileClasspath += fabric
            sourceSet.runtimeClasspath += fabric
        }

        for (sourceSet in provider.serverSourceSets) {
            sourceSet.compileClasspath += fabric
            sourceSet.runtimeClasspath += fabric
        }

    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar = minecraft

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar =
        if (accessWidener != null) {
            val output = MinecraftJar(
                baseMinecraft,
                parentPath = project.unimined.getLocalCache().resolve("fabric").createDirectories()
            )
            if (!output.path.exists() || project.gradle.startParameter.isRefreshDependencies) {
                if (AccessWidenerMinecraftTransformer.transform(
                        accessWidener!!.toPath(),
                        baseMinecraft.mappingNamespace,
                        baseMinecraft.path,
                        output.path,
                        false,
                        project.logger
                    )
                ) {
                    output
                } else {
                    baseMinecraft
                }
            } else {
                output
            }
        } else baseMinecraft

    protected fun getIntermediaryClassPath(envType: EnvType): String {
        val remapClasspath = project.unimined.getLocalCache().resolve("remapClasspath.txt")
        val s = provider.mcLibraries.files.joinToString(":") + ":" +
                project.unimined.modProvider.modRemapper.preTransform(envType).joinToString(":") + ":" +
                provider.getMinecraftWithMapping(envType, prodNamespace, prodNamespace)
        remapClasspath.writeText(s, options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        return remapClasspath.absolutePathString()
    }

    override fun afterRemapJarTask(output: Path) {
        insertIncludes(output)
        insertAW(output)
    }

    private fun insertIncludes(output: Path) {
        ZipReader.openZipFileSystem(output, mapOf("mutable" to true)).use { fs ->
            val mod = fs.getPath(modJsonName)
            if (!Files.exists(mod)) {
                throw IllegalStateException("$modJsonName not found in jar")
            }
            val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(mod))).asJsonObject

            Files.createDirectories(fs.getPath("META-INF/jars/"))
            val includeCache = project.unimined.getLocalCache().resolve("includeCache")
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
                        val innermod = innerfs.getPath(modJsonName)
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

                addIncludeToModJson(json, dep, "META-INF/jars/${dep.name}-${dep.version}.jar")
            }
            Files.write(mod, GSON.toJson(json).toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
        }
    }

    protected abstract fun addIncludeToModJson(json: JsonObject, dep: Dependency, path: String)

    private fun insertAW(output: Path) {
        if (accessWidener != null) {
            ZipReader.openZipFileSystem(output, mapOf("mutable" to true)).use { fs ->
                val mod = fs.getPath(modJsonName)
                if (!Files.exists(mod)) {
                    throw IllegalStateException("$modJsonName not found in jar")
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
                json.addProperty(accessWidenerJsonKey, awPath.toString())
                Files.write(mod, GSON.toJson(json).toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
            }
        }
    }
}