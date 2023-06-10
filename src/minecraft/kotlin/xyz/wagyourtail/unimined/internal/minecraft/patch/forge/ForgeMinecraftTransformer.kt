package xyz.wagyourtail.unimined.internal.minecraft.patch.forge

import com.google.gson.JsonParser
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.task.ExportMappingsTask
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.task.ExportMappingsTaskImpl
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.fg2.FG2MinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.resolver.parseAllLibraries
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.fg1.FG1MinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.fg3.FG3MinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod.JarModAgentMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.transform.merge.ClassMerger
import xyz.wagyourtail.unimined.util.*
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class ForgeMinecraftTransformer(project: Project, provider: MinecraftProvider):
        AbstractMinecraftTransformer(project, provider, "forge"), ForgePatcher {

    val forge: Configuration = project.configurations.maybeCreate("forge".withSourceSet(provider.sourceSet))

    @get:ApiStatus.Internal
    var forgeTransformer: JarModMinecraftTransformer by FinalizeOnWrite(MustSet())

    override var accessTransformer: File? = null

    override var customSearge: Boolean by FinalizeOnRead(false)

    fun transforms(transform: String) {
        if (forgeTransformer !is JarModAgentMinecraftTransformer) {
            throw IllegalStateException("JarModAgentPatcher is not enabled")
        }
        (forgeTransformer as JarModAgentMinecraftTransformer).transforms(transform)
    }

    fun transforms(transforms: List<String>) {
        if (forgeTransformer !is JarModAgentMinecraftTransformer) {
            throw IllegalStateException("JarModAgentPatcher is not enabled")
        }
        (forgeTransformer as JarModAgentMinecraftTransformer).transforms(transforms)
    }

    override val prodNamespace: MappingNamespaceTree.Namespace
        get() = forgeTransformer.prodNamespace

    override var deleteMetaInf: Boolean
        get() = forgeTransformer.deleteMetaInf
        set(value) {
            forgeTransformer.deleteMetaInf = value
        }

    override var mixinConfig: List<String> = mutableListOf()

    private val sideMarkers = mapOf(
        "net/minecraftforge/fml/relauncher/SideOnly" to Triple(
            "net/minecraftforge/fml/relauncher/Side", "value", mapOf(
                EnvType.CLIENT to "CLIENT",
                EnvType.SERVER to "SERVER"
            )
        ),
        "cpw/mods/fml/relauncher/SideOnly" to Triple(
            "cpw/mods/fml/relauncher/Side", "value", mapOf(
                EnvType.CLIENT to "CLIENT",
                EnvType.SERVER to "SERVER"
            )
        ),
        "cpw/mods/fml/common/asm/SideOnly" to Triple(
            "cpw/mods/fml/common/Side", "value", mapOf(
                EnvType.CLIENT to "CLIENT",
                EnvType.SERVER to "SERVER"
            )
        ),
    )

    override val remapAtToLegacy: Boolean by lazy {
        provider.minecraftData.mcVersionCompare(provider.version, "1.7.10") < 0
    }

    override fun beforeMappingsResolve() {
        forgeTransformer.beforeMappingsResolve()
    }

    override fun forge(dep: Any, action: Dependency.() -> Unit) {
        forge.dependencies.add(if (dep is String && !dep.contains(":")) {
            if (!canCombine) {
                if (provider.side == EnvType.COMBINED) throw IllegalStateException("Cannot use forge dependency pre 1.3 without specifying non-combined side")
                project.dependencies.create("net.minecraftforge:forge:${provider.version}-${dep}:${provider.side.classifier}@zip")
            } else {
                val zip = provider.minecraftData.mcVersionCompare(provider.version, "1.6") < 0
                project.dependencies.create("net.minecraftforge:forge:${provider.version}-${dep}:universal@${if (zip) "zip" else "jar"}")
            }
        } else {
            project.dependencies.create(dep)
        }.apply(action))

        if (forge.dependencies.isEmpty()) {
            throw IllegalStateException("No forge dependency found!")
        }

        if (forge.dependencies.size > 1) {
            throw IllegalStateException("Multiple forge dependencies found, make sure you only have one forge dependency!")
        }

        val forgeDep = forge.dependencies.first()

        if (forgeDep.group != "net.minecraftforge" || !(forgeDep.name == "minecraftforge" || forgeDep.name == "forge")) {
            throw IllegalStateException("Invalid forge dependency found, if you are using multiple dependencies in the forge configuration, make sure the last one is the forge dependency!")
        }

        forgeTransformer = if (provider.minecraftData.mcVersionCompare(provider.version, "1.3") < 0) {
            FG1MinecraftTransformer(project, this)
        } else {
            val jar = forge.files(forgeDep).first { it.extension == "zip" || it.extension == "jar" }
            determineForgeProviderFromUniversal(jar)
        }
    }

    private val actualSideMarker by lazy {
        if (forge.dependencies.isEmpty()) return@lazy null // pre 1.3 - see below
        val forgeUniversal = forge.dependencies.last()
        val forgeJar = forge.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }

        val type = forgeJar.toPath().readZipContents().map {
            it.substringBefore(".class") to sideMarkers[it.substringBefore(".class")]
        }.filter { it.second != null }.map { it.first to it.second!! }
        if (type.size > 1) throw IllegalStateException("Found more than one side marker in forge jar: $type")
        if (type.isEmpty()) {
            project.logger.warn("[Unimined/ForgeTransformer] No side marker found in forge jar, using default (none)")
            return@lazy null
        }
        type.first()
    }

    private fun applyAnnotationVisitor(visitor: AnnotationVisitor, env: EnvType) {
        if (actualSideMarker == null) return
        visitor.visitEnum(
            actualSideMarker!!.second.second,
            "L${actualSideMarker!!.second.first};",
            actualSideMarker!!.second.third[env]
        )
        visitor.visitEnd()
    }

    override val merger: ClassMerger = ClassMerger(
        { node, env ->
            if (env == EnvType.COMBINED) return@ClassMerger
            if (actualSideMarker == null) return@ClassMerger
            // already has
            if (node.visibleAnnotations?.any { it.desc == "L${actualSideMarker!!.first};" } == true) return@ClassMerger
            // anonymous class
            if (isAnonClass(node)) return@ClassMerger
            val visitor = node.visitAnnotation("L${actualSideMarker!!.first};", true)
            applyAnnotationVisitor(visitor, env)
        },
        { node, env ->
            if (env == EnvType.COMBINED) return@ClassMerger
            if (actualSideMarker == null) return@ClassMerger
            if (node.visibleAnnotations?.any { it.desc == "L${actualSideMarker!!.first};" } == true) return@ClassMerger
            val visitor = node.visitAnnotation("L${actualSideMarker!!.first};", true)
            applyAnnotationVisitor(visitor, env)
        },
        { node, env ->
            if (env == EnvType.COMBINED) return@ClassMerger
            if (actualSideMarker == null) return@ClassMerger
            if (node.visibleAnnotations?.any { it.desc == "L${actualSideMarker!!.first};" } == true) return@ClassMerger
            val visitor = node.visitAnnotation("L${actualSideMarker!!.first};", true)
            applyAnnotationVisitor(visitor, env)
        }
    )


    @ApiStatus.Internal
    var tweakClassClient: String? = null

    @ApiStatus.Internal
    var tweakClassServer: String? = null

    @ApiStatus.Internal
    internal var mainClass: String? = null

    override fun aw2at(input: String): File = aw2at(File(input))

    override fun aw2at(input: String, output: String) = aw2at(File(input), File(output))

    override fun aw2at(input: File) = aw2at(
        input,
        project.extensions.getByType(SourceSetContainer::class.java).getByName("main").resources.srcDirs.first()
            .resolve("META-INF/accesstransformer.cfg")
    )

    override fun aw2at(input: File, output: File): File {
        return AccessTransformerMinecraftTransformer.aw2at(input.toPath(), output.toPath()).toFile()
    }

    override fun aw2atLegacy(input: String): File = aw2atLegacy(File(input))

    override fun aw2atLegacy(input: String, output: String) = aw2atLegacy(File(input), File(output))

    override fun aw2atLegacy(input: File) = aw2atLegacy(
        input,
        project.extensions.getByType(SourceSetContainer::class.java).getByName("main").resources.srcDirs.first()
            .resolve("META-INF/accesstransformer.cfg")
    )

    override fun aw2atLegacy(input: File, output: File): File {
        return AccessTransformerMinecraftTransformer.aw2at(input.toPath(), output.toPath(), true).toFile()
    }

    override fun at2aw(input: String, output: String, namespace: MappingNamespaceTree.Namespace) =
        at2aw(File(input), File(output), namespace)

    override fun at2aw(input: String, namespace: MappingNamespaceTree.Namespace) = at2aw(File(input), namespace)
    override fun at2aw(input: String, output: String) = at2aw(File(input), File(output))
    override fun at2aw(input: String) = at2aw(File(input))
    override fun at2aw(input: File) = at2aw(input, provider.mappings.devNamespace)
    override fun at2aw(input: File, namespace: MappingNamespaceTree.Namespace) = at2aw(
        input,
        project.extensions.getByType(SourceSetContainer::class.java).getByName("main").resources.srcDirs.first()
            .resolve("${project.name}.accesswidener"),
        namespace
    )

    override fun at2aw(input: File, output: File) = at2aw(input, output, provider.mappings.devNamespace)
    override fun at2aw(input: File, output: File, namespace: MappingNamespaceTree.Namespace): File {
        return AccessTransformerMinecraftTransformer.at2aw(
            input.toPath(),
            output.toPath(),
            namespace.name,
            provider.mappings.mappingTree,
            project.logger
        ).toFile()
    }

    @get:ApiStatus.Internal
    @set:ApiStatus.Experimental
    var srgToMCPAsSRG: Path by FinalizeOnRead(LazyMutable {
        project.unimined.getLocalCache().resolve("mappings").createDirectories().resolve(provider.mappings.combinedNames).resolve("srg2mcp.srg").apply {
            val export = ExportMappingsTaskImpl.ExportImpl(provider.mappings).apply {
                location = toFile()
                type = ExportMappingsTask.MappingExportTypes.SRG
                sourceNamespace = provider.mappings.getNamespace("searge")
                targetNamespace = setOf(provider.mappings.devNamespace)
            }
            export.validate()
            export.exportFunc(provider.mappings.mappingTree)
        }
    })

    @get:ApiStatus.Internal
    @set:ApiStatus.Experimental
    var srgToMCPAsMCP: Path by FinalizeOnRead(LazyMutable {
        project.unimined.getLocalCache().resolve("mappings").createDirectories().resolve(provider.mappings.combinedNames).resolve("srg2mcp.jar").apply {
            val export = ExportMappingsTaskImpl.ExportImpl(provider.mappings).apply {
                location = toFile()
                type = ExportMappingsTask.MappingExportTypes.MCP
                sourceNamespace = provider.mappings.getNamespace("searge")
                skipComments = true // the reader forge uses now is too dumb...
                targetNamespace = setOf(provider.mappings.devNamespace)
                envType = provider.side
            }
            export.validate()
            export.exportFunc(provider.mappings.mappingTree)
        }
    })

    init {
        project.unimined.forgeMaven()
    }

    override fun apply() {
        val forgeDep = forge.dependencies.first()

        // test if pre unified jar
        if (provider.minecraftData.mcVersionCompare(provider.version, "1.3") > 0) {
            val jar = forge.files(forgeDep).first { it.extension == "zip" || it.extension == "jar" }

            //parse version json from universal jar and apply
            jar.toPath().readZipInputStreamFor("version.json", false) {
                JsonParser.parseReader(InputStreamReader(it)).asJsonObject
            }?.let { versionJson ->
                val libraries = parseAllLibraries(versionJson.getAsJsonArray("libraries"))
                mainClass = versionJson.get("mainClass").asString
                val args = versionJson.get("minecraftArguments").asString
                provider.addLibraries(libraries.filter {
                    !it.name.startsWith("net.minecraftforge:minecraftforge:") && !it.name.startsWith(
                        "net.minecraftforge:forge:"
                    )
                })
                tweakClassClient = args.split("--tweakClass")[1].trim()
            }
        }

        forgeTransformer.apply()
        super.apply()
    }

    private fun determineForgeProviderFromUniversal(universal: File): JarModMinecraftTransformer {
        val files = mutableSetOf<ForgeFiles>()
        universal.toPath().forEachInZip { path, _ ->
            if (ForgeFiles.ffMap.contains(path)) {
                files.add(ForgeFiles.ffMap[path]!!)
            }
        }

        var forgeTransformer: JarModMinecraftTransformer? = null
        for (vers in ForgeVersion.values()) {
            if (files.containsAll(vers.accept) && files.none { it in vers.deny }) {
                project.logger.debug("[Unimined/ForgeTransformer] Files $files")
                forgeTransformer = when (vers) {
                    ForgeVersion.FG1 -> {
                        project.logger.lifecycle("[Unimined/ForgeTransformer] Selected FG1")
                        FG1MinecraftTransformer(project, this)
                    }

                    ForgeVersion.FG2 -> {
                        project.logger.lifecycle("[Unimined/ForgeTransformer] Selected FG2")
                        FG2MinecraftTransformer(project, this)
                    }

                    ForgeVersion.FG3 -> {
                        project.logger.lifecycle("[Unimined/ForgeTransformer] Selected FG3")
                        FG3MinecraftTransformer(project, this)
                    }
                }
                break
            }
        }

        if (forgeTransformer == null) {
            throw IllegalStateException("Unable to determine forge version from universal jar!")
        }
        // TODO apply some additional properties at this time

        return forgeTransformer
    }

    override fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar): MinecraftJar {
        return forgeTransformer.merge(clientjar, serverjar)
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        return forgeTransformer.transform(minecraft)
    }

    enum class ForgeFiles(val path: String) {
        FORGE_AT("META-INF/accesstransformer.cfg"),
        OLD_FORGE_AT("forge_at.cfg"),
        BINPATCHES_PACK("binpatches.pack.lzma"),
        JAR_PATCHES("net/minecraft/client/Minecraft.class"),
        VERSION_JSON("version.json"),
        ;

        companion object {
            val ffMap = mutableMapOf<String, ForgeFiles>()

            init {
                for (entry in ForgeFiles.values()) {
                    ffMap[entry.path] = entry
                }
            }
        }
    }

    enum class ForgeVersion(
        val accept: Set<ForgeFiles> = setOf(),
        val deny: Set<ForgeFiles> = setOf(),
    ) {
        FG1(
            setOf(
                ForgeFiles.JAR_PATCHES
            ),
            setOf(
                ForgeFiles.FORGE_AT,
                ForgeFiles.BINPATCHES_PACK,
                ForgeFiles.VERSION_JSON,
            )
        ),
        FG2(
            setOf(
                ForgeFiles.OLD_FORGE_AT,
                ForgeFiles.BINPATCHES_PACK,
                ForgeFiles.VERSION_JSON
            ),
            setOf(
                ForgeFiles.JAR_PATCHES,
                ForgeFiles.FORGE_AT
            )
        ),
        FG3(
            setOf(
            ),
            setOf(
                ForgeFiles.JAR_PATCHES,
                ForgeFiles.VERSION_JSON
            )
        ),
    }

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar {
        return forgeTransformer.afterRemap(baseMinecraft)
    }

    fun applyATs(baseMinecraft: MinecraftJar, ats: List<Path>): MinecraftJar {
        project.logger.lifecycle("[Unimined/ForgeTransformer] Applying ATs $ats")
        return if (accessTransformer != null) {
            project.logger.lifecycle("[Unimined/ForgeTransformer] Using user access transformer $accessTransformer")
            val output = MinecraftJar(
                baseMinecraft,
                parentPath = project.unimined.getLocalCache().resolve("forge"),
                awOrAt = "at+${accessTransformer!!.toPath().getSha1()}"
            )
            if (!output.path.exists() || project.unimined.forceReload) {
                AccessTransformerMinecraftTransformer.transform(
                    ats + listOf(accessTransformer!!.toPath()),
                    baseMinecraft.path,
                    output.path
                )
            }
            output
        } else {
            if (ats.isEmpty()) {
                return baseMinecraft
            }
            val output = MinecraftJar(baseMinecraft, awOrAt = "at")
            if (!output.path.exists() || project.unimined.forceReload) {
                AccessTransformerMinecraftTransformer.transform(ats, baseMinecraft.path, output.path)
            }
            output
        }
    }

    override fun applyClientRunTransform(config: RunConfig) {
        project.logger.info("[Unimined/ForgeTransformer] Adding mixin config $mixinConfig to client run config")
        forgeTransformer.applyClientRunTransform(config)
        for (mixin in mixinConfig) {
            config.args += listOf("--mixin", mixin)
        }
    }

    override fun applyServerRunTransform(config: RunConfig) {
        project.logger.info("[Unimined/ForgeTransformer] Adding mixin config $mixinConfig to server run config")
        forgeTransformer.applyServerRunTransform(config)
        for (mixin in mixinConfig) {
            config.args += listOf("--mixin", mixin)
        }
    }

    override fun afterRemapJarTask(remapJarTask: RemapJarTask, output: Path) {
        //TODO: JarJar
    }

    override fun applyExtraLaunches() {
        forgeTransformer.applyExtraLaunches()
    }

    private fun applyToTask(container: TaskContainer) {
        for (jar in container.withType(Jar::class.java)) {
            jar.manifest {
                it.attributes["MixinConfigs"] = mixinConfig.joinToString(",")
            }
        }
    }

    override fun name(): String {
        return forgeTransformer.name()
    }

}