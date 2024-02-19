package xyz.wagyourtail.unimined.internal.minecraft.patch.fabric

import com.google.gson.*
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.patch.ataw.AccessWidenerPatcher
import xyz.wagyourtail.unimined.api.minecraft.patch.fabric.FabricLikePatcher
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.mapping.task.ExportMappingsTask
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.api.uniminedMaybe
import xyz.wagyourtail.unimined.internal.mapping.ii.InterfaceInjectionMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.task.ExportMappingsTaskImpl
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.api.minecraft.MinecraftJar
import xyz.wagyourtail.unimined.internal.minecraft.patch.access.widener.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.resolver.Library
import xyz.wagyourtail.unimined.internal.minecraft.transform.merge.ClassMerger
import xyz.wagyourtail.unimined.util.*
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

abstract class FabricLikeMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider,
    providerName: String,
    val modJsonName: String,
    val accessWidenerJsonKey: String,
    private val accessWidenerTransformer: AccessWidenerMinecraftTransformer = AccessWidenerMinecraftTransformer(project, provider, providerName),
): AbstractMinecraftTransformer(
    project,
    provider,
    providerName
), FabricLikePatcher, AccessWidenerPatcher by accessWidenerTransformer {

    companion object {
        val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    val fabric: Configuration = project.configurations.maybeCreate(providerName.withSourceSet(provider.sourceSet)).also {
        provider.minecraftLibraries.extendsFrom(it)
    }

    private val fabricJson: Configuration = project.configurations.detachedConfiguration()

    private val include: Configuration = project.configurations.maybeCreate("include".withSourceSet(provider.sourceSet))

    override var customIntermediaries: Boolean by FinalizeOnRead(false)

    override var skipInsertAw: Boolean by FinalizeOnRead(false)

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

    override var prodNamespace by FinalizeOnRead(LazyMutable { provider.mappings.getNamespace("intermediary") })

    @get:ApiStatus.Internal
    @set:ApiStatus.Experimental
    override var devMappings: Path? by FinalizeOnRead(LazyMutable {
        provider.localCache
            .resolve("mappings")
            .createDirectories()
            .resolve("intermediary2named.jar")
            .apply {
                val export = ExportMappingsTaskImpl.ExportImpl(provider.mappings).apply {
                    location = toFile()
                    type = ExportMappingsTask.MappingExportTypes.TINY_V2
                    sourceNamespace = prodNamespace
                    targetNamespace = setOf(provider.mappings.devNamespace)
                    renameNs[provider.mappings.devNamespace] = "named"
                }
                export.validate()
                export.exportFunc(provider.mappings.mappingTree)
            }
    })

    init {
        addMavens()
    }

    override fun prodNamespace(namespace: String) {
        val delegate: FinalizeOnRead<MappingNamespaceTree.Namespace> = FabricLikeMinecraftTransformer::class.getField("prodNamespace")!!.getDelegate(this) as FinalizeOnRead<MappingNamespaceTree.Namespace>
        delegate.setValueIntl(LazyMutable { provider.mappings.getNamespace(namespace) })
    }

    @Deprecated("", replaceWith = ReplaceWith("prodNamespace(namespace)"))
    override fun setProdNamespace(namespace: String) {
        prodNamespace(namespace)
    }

    protected abstract fun addMavens()
    protected abstract fun addIntermediaryMappings()

    var mainClass: JsonObject? = null

    override fun beforeMappingsResolve() {
        if (!customIntermediaries) {
            addIntermediaryMappings()
        }
    }

    val fabricDep by lazy {
        val dependencies = fabric.dependencies

        if (dependencies.isEmpty()) {
            throw IllegalStateException("No dependencies found for fabric provider")
        }

        if (dependencies.size > 1) {
            throw IllegalStateException("Multiple dependencies found for fabric provider")
        }

        dependencies.first()
    }

    override fun apply() {
        val client = provider.side == EnvType.CLIENT || provider.side == EnvType.COMBINED
        val server = provider.side == EnvType.SERVER || provider.side == EnvType.COMBINED

        var artifactString = ""
        if (fabricDep.group != null) {
            artifactString += fabricDep.group + ":"
        }
        artifactString += fabricDep.name
        if (fabricDep.version != null) {
            artifactString += ":" + fabricDep.version
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
                createFabricLoaderDependency(it)
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
            libraries.get("development")?.asJsonArray?.forEach {
                createFabricLoaderDependency(it)
            }
        }

        mainClass = json.get("mainClass")?.asJsonObject

        if (devMappings != null) {
            provider.minecraftLibraries.dependencies.add(
                project.dependencies.create(project.files(devMappings))
            )
        }

        // mixins get remapped at runtime, so we don't need to on fabric
        provider.mods.default {
            mixinRemap {
                off()
            }
        }

        super.apply()
    }

    private fun createFabricLoaderDependency(it: JsonElement) {
        val dep: ModuleDependency = project.dependencies.create(
            it.asJsonObject.get("name").asString
        ) as ModuleDependency
        dep.isTransitive = false
        provider.minecraftLibraries.dependencies.add(dep)
    }

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar = applyInterfaceInjection(accessWidenerTransformer.afterRemap(baseMinecraft))

    private fun applyInterfaceInjection(baseMinecraft: MinecraftJar): MinecraftJar {
        val injections = hashMapOf<String, List<String>>()

        this.collectInterfaceInjections(baseMinecraft, injections)

        return if (injections.isNotEmpty()) {
            val oldSuffix = if (baseMinecraft.awOrAt != null) baseMinecraft.awOrAt + "+" else ""

            val output = MinecraftJar(
                baseMinecraft,
                parentPath = provider.localCache.resolve("fabric").createDirectories(),
                awOrAt = "${oldSuffix}ii+${injections.getShortSha1()}"
            )

            if (!output.path.exists() || project.unimined.forceReload) {
                if (InterfaceInjectionMinecraftTransformer.transform(
                        injections,
                        baseMinecraft.path,
                        output.path,
                        project.logger
                    )
                ) {
                    output
                } else baseMinecraft
            } else output
        } else baseMinecraft
    }

    abstract fun collectInterfaceInjections(baseMinecraft: MinecraftJar, injections: HashMap<String, List<String>>)
    fun collectInterfaceInjections(baseMinecraft: MinecraftJar, injections: HashMap<String, List<String>>, interfaces: JsonObject) {
        injections.putAll(interfaces.entrySet()
            .filterNotNull()
            .filter { it.key != null && it.value != null && it.value.isJsonArray }
            .map {
                val element = it.value!!

                Pair(it.key!!, if (element.isJsonArray) {
                    element.asJsonArray.mapNotNull { name -> name.asString }
                } else arrayListOf())
            }
            .map {
                var target = it.first

                val clazz = provider.mappings.mappingTree.getClass(
                    target,
                    provider.mappings.mappingTree.getNamespaceId(prodNamespace.name)
                )

                if (clazz != null) {
                    var newTarget = clazz.getName(provider.mappings.mappingTree.getNamespaceId(baseMinecraft.mappingNamespace.name))

                    if (newTarget == null) {
                        newTarget = clazz.getName(provider.mappings.mappingTree.getNamespaceId(baseMinecraft.fallbackNamespace.name))
                    }

                    if (newTarget != null) {
                        target = newTarget
                    }
                }

                Pair(target, it.second)
            }
        )
    }

    val intermediaryClasspath: Path = provider.localCache.resolve("remapClasspath.txt".withSourceSet(provider.sourceSet))

    override fun afterEvaluate() {
        project.logger.lifecycle("[Unimined/Fabric] Generating intermediary classpath.")
        // resolve intermediary classpath
        val classpath = (provider.mods.getClasspathAs(
            prodNamespace,
            prodNamespace,
            provider.sourceSet.runtimeClasspath.filter { !provider.isMinecraftJar(it.toPath()) }.toSet()
        ) + provider.getMinecraft(prodNamespace, prodNamespace).toFile()).filter { it.exists() && !it.isDirectory }
        // write to file
        intermediaryClasspath.writeText(classpath.joinToString(File.pathSeparator), options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
    }

    override fun afterRemapJarTask(remapJarTask: RemapJarTask, output: Path) {
        insertIncludes(output)
        insertAW(output)
    }

    private fun insertIncludes(output: Path) {
        output.openZipFileSystem(mapOf("mutable" to true)).use { fs ->
            val mod = fs.getPath(modJsonName)
            if (!Files.exists(mod)) {
                throw IllegalStateException("$modJsonName not found in jar")
            }
            val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(mod))).asJsonObject

            Files.createDirectories(fs.getPath("META-INF/jars/"))
            val includeCache = provider.localCache.resolve("includeCache")
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

                    cachePath.openZipFileSystem(mapOf("mutable" to true)).use { innerfs ->
                        val innermod = innerfs.getPath(modJsonName)
                        if (!Files.exists(innermod)) {
                            val innerjson = JsonObject()
                            innerjson.addProperty("schemaVersion", 1)
                            var artifactString = ""
                            if (dep.group != null) {
                                artifactString += dep.group!!.replace(".", "_") + "_"
                            }
                            artifactString += dep.name

                            innerjson.addProperty("id", artifactString.lowercase())
                            innerjson.addProperty("version", dep.version)
                            innerjson.addProperty("name", dep.name)
                            val custom = JsonObject()
                            custom.addProperty("fabric-loom:generated", true)
                            custom.addProperty("unimined:generated", true)
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
        if (accessWidener != null && !skipInsertAw) {
            output.openZipFileSystem(mapOf("mutable" to true)).use { fs ->
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

    val groups: String by lazy {
        val groups = sortProjectSourceSets().mapValues { it.value.toMutableSet() }.toMutableMap()
        // detect non-fabric groups
        for ((proj, sourceSet) in groups.keys.toSet()) {
            if (proj.uniminedMaybe?.minecrafts?.map?.get(sourceSet)?.mcPatcher !is FabricLikePatcher) {
                // merge with current
                proj.logger.warn("[Unimined/FabricLike] Non-fabric ${(proj to sourceSet).toPath()} found in fabric classpath groups, merging with current (${(project to provider.sourceSet).toPath()}), this should've been manually specified with `combineWith`")
                groups[this.project to this.provider.sourceSet]!! += groups[proj to sourceSet]!!
                groups.remove(proj to sourceSet)
            }
        }
        project.logger.info("[Unimined/FabricLike] Classpath groups: ${groups.map { it.key.toPath() + " -> " + it.value.joinToString(", ") { it.toPath() } }.joinToString("\n    ")}")
        groups.map { entry -> entry.value.flatMap { it.second.output }.joinToString(File.pathSeparator) { it.absolutePath } }.joinToString(File.pathSeparator.repeat(2))
    }

    override fun applyClientRunTransform(config: RunConfig) {
        config.mainClass = mainClass?.get("client")?.asString ?: config.mainClass
    }

    override fun applyServerRunTransform(config: RunConfig) {
        config.mainClass = mainClass?.get("server")?.asString ?: config.mainClass
    }

    override fun libraryFilter(library: Library): Boolean {
        // fabric provides its own asm, exclude asm-all from vanilla minecraftLibraries
        return !library.name.startsWith("org.ow2.asm:asm-all")
    }

    fun getModJsonPath(): File? {
        val json = provider.sourceSet.resources.firstOrNull { it.name.equals(modJsonName) }
        if (json == null) {
            project.logger.warn("[Unimined/FabricLike] $modJsonName not found in sourceSet ${provider.project.path} ${provider.sourceSet.name}")
            return null
        }
        return json
    }
}