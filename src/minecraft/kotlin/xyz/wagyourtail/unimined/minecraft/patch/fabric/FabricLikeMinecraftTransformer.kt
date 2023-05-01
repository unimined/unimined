package xyz.wagyourtail.unimined.minecraft.patch.fabric

import com.google.gson.*
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.FabricLikePatcher
import xyz.wagyourtail.unimined.api.tasks.MappingExportTypes
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.mappings.MappingExportImpl
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.minecraft.patch.forge.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.transform.merge.ClassMerger
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
    providerName: String,
    val modJsonName: String,
    val accessWidenerJsonKey: String
): AbstractMinecraftTransformer(
    project,
    provider,
    providerName
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

    protected abstract val ENVIRONMENT: String
    protected abstract val ENV_TYPE: String

    override val merger: ClassMerger = ClassMerger(
        { node, env ->
            if (env == EnvType.COMBINED) return@ClassMerger
            if (isAnonClass(node)) return@ClassMerger
            val visitor = node.visitAnnotation(ENVIRONMENT, true)
            visitor.visitEnum("value", ENV_TYPE, env.name)
            visitor.visitEnd()
        },
        { node, env ->
            if (env != EnvType.COMBINED) {
                val visitor = node.visitAnnotation(ENVIRONMENT, true)
                visitor.visitEnum("value", ENV_TYPE, env.name)
                visitor.visitEnd()
            }
        },
        { node, env ->
            if (env != EnvType.COMBINED) {
                val visitor = node.visitAnnotation(ENVIRONMENT, true)
                visitor.visitEnum("value", ENV_TYPE, env.name)
                visitor.visitEnd()
            }
        }
    )

    override var prodNamespace = MappingNamespace.INTERMEDIARY


    override var devNamespace by LazyMutable {
        MappingNamespace.findByType(
            MappingNamespace.Type.NAMED,
            project.mappings.getAvailableMappings(project.minecraft.defaultEnv)
        )
    }
    override var devFallbackNamespace by LazyMutable {
        MappingNamespace.findByType(
            MappingNamespace.Type.INT,
            project.mappings.getAvailableMappings(project.minecraft.defaultEnv)
        )
    }

    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    var devMappings: Path? by LazyMutable {
        project.unimined.getLocalCache()
            .resolve("mappings")
            .createDirectories()
            .resolve("intermediary2named.jar")
            .apply {
                val export = MappingExportImpl(EnvType.COMBINED).apply {
                    location = toFile()
                    type = MappingExportTypes.TINY_V2
                    sourceNamespace = MappingNamespace.OFFICIAL
                    targetNamespace = listOf(prodNamespace, devNamespace)
                    renameNs[devNamespace] = "named"
                }
                export.validate()
                export.exportFunc(project.mappings.getMappingTree(EnvType.COMBINED))
            }
    }

    init {
        addMavens()
    }

    protected abstract fun addMavens()

    override fun afterEvaluate() {
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
                    createFabricLoaderDependency(it)
                }
                if (server) {
                    createFabricLoaderDependency(it)
                }
            }
            if (client) {
                libraries.get("client")?.asJsonArray?.forEach {
                    createFabricLoaderDependency(it)
                }
            }
            if (server) {
                libraries.get("server")?.asJsonArray?.forEach {
                    createFabricLoaderDependency(it)
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

        if (devMappings != null) {
            provider.mcLibraries.dependencies.add(
                project.dependencies.create(project.files(devMappings))
            )
        }

        super.afterEvaluate()
    }

    private fun createFabricLoaderDependency(it: JsonElement) {
        val dep: ModuleDependency = project.dependencies.create(
            it.asJsonObject.get("name").asString
        ) as ModuleDependency
        dep.isTransitive = false
        provider.mcLibraries.dependencies.add(dep)
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
        val s = arrayOf(
            provider.mcLibraries.files.joinToString(File.pathSeparator),
            project.unimined.modProvider.modRemapper.preTransform(envType).joinToString(File.pathSeparator),
            provider.getMinecraftWithMapping(envType, prodNamespace, prodNamespace).toString()
        ).filter { it.isNotEmpty() }.joinToString(File.pathSeparator)

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


    override fun at2aw(input: String, output: String, namespace: MappingNamespace) =
        at2aw(File(input), File(output), namespace)

    override fun at2aw(input: String, namespace: MappingNamespace) = at2aw(File(input), namespace)
    override fun at2aw(input: String, output: String) = at2aw(File(input), File(output))
    override fun at2aw(input: String) = at2aw(File(input))
    override fun at2aw(input: File) = at2aw(input, devNamespace)
    override fun at2aw(input: File, namespace: MappingNamespace) = at2aw(
        input,
        project.extensions.getByType(SourceSetContainer::class.java).getByName("main").resources.srcDirs.first()
            .resolve("${project.name}.accesswidener"),
        namespace
    )

    override fun at2aw(input: File, output: File) = at2aw(input, output, devNamespace)
    override fun at2aw(input: File, output: File, namespace: MappingNamespace): File {
        return AccessTransformerMinecraftTransformer.at2aw(
            input.toPath(),
            output.toPath(),
            namespace.namespace,
            project.mappings.getMappingTree(EnvType.COMBINED),
            project.logger
        ).toFile()
    }

    override fun mergeAws(inputs: List<File>): File {
        return mergeAws(
            devNamespace,
            inputs
        )
    }

    override fun mergeAws(namespace: MappingNamespace, inputs: List<File>): File {
        return mergeAws(
            project.extensions.getByType(SourceSetContainer::class.java).getByName("main").resources.srcDirs.first()
                .resolve("${project.name}.accesswidener"),
            namespace, inputs
        )
    }

    override fun mergeAws(output: File, inputs: List<File>): File {
        return mergeAws(output, devNamespace, inputs)
    }

    override fun mergeAws(output: File, namespace: MappingNamespace, inputs: List<File>): File {
        return AccessWidenerMinecraftTransformer.mergeAws(
            inputs.map { it.toPath() },
            output.toPath(),
            namespace,
            project.mappings,
            provider
        ).toFile()
    }
}