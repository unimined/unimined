package xyz.wagyourtail.unimined.minecraft.patch.forge

import com.google.gson.JsonParser
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.launch.LaunchConfig
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.tasks.MappingExportTypes
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.mappings.MappingExportImpl
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.minecraft.patch.forge.fg1.FG1MinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.forge.fg2.FG2MinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.forge.fg3.FG3MinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.resolve.parseAllLibraries
import xyz.wagyourtail.unimined.minecraft.transform.merge.ClassMerger
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.getSha1
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class ForgeMinecraftTransformer(project: Project, provider: MinecraftProviderImpl) :
        AbstractMinecraftTransformer(project, provider, Constants.FORGE_PROVIDER), ForgePatcher {

    val forge: Configuration = project.configurations.maybeCreate(Constants.FORGE_PROVIDER)

    @ApiStatus.Internal
    lateinit var forgeTransformer: JarModMinecraftTransformer

    override var accessTransformer: File? = null
    override var mcpVersion: String? = null
    override var mcpChannel: String? = null

    override val prodNamespace: MappingNamespace
        get() = forgeTransformer.prodNamespace

    override var deleteMetaInf: Boolean
        get() = forgeTransformer.deleteMetaInf
        set(value) {
            forgeTransformer.deleteMetaInf = value
        }

    override var devNamespace by LazyMutable { MappingNamespace.findByType(MappingNamespace.Type.NAMED, project.mappings.getAvailableMappings(project.minecraft.defaultEnv)) }
    override var devFallbackNamespace by LazyMutable { MappingNamespace.findByType(MappingNamespace.Type.INT, project.mappings.getAvailableMappings(project.minecraft.defaultEnv)) }

    override var mixinConfig: List<String> = mutableListOf()


    private val sideMarkers = mapOf(
        "net/minecraftforge/fml/relauncher/SideOnly" to Triple("net/minecraftforge/fml/relauncher/Side", "value", mapOf(
            EnvType.CLIENT to "CLIENT",
            EnvType.SERVER to "SERVER"
        )),
        "cpw/mods/fml/relauncher/SideOnly" to Triple("cpw/mods/fml/relauncher/Side", "value", mapOf(
            EnvType.CLIENT to "CLIENT",
            EnvType.SERVER to "SERVER"
        )),
        "cpw/mods/fml/common/asm/SideOnly" to Triple("cpw/mods/fml/common/Side", "value", mapOf(
            EnvType.CLIENT to "CLIENT",
            EnvType.SERVER to "SERVER"
        )),
    )

    private val actualSideMarker by lazy {
        if (forge.dependencies.isEmpty()) return@lazy null // pre 1.3 - see below
        val forgeUniversal = forge.dependencies.last()
        val forgeJar = forge.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }

        val type = ZipReader.readContents(forgeJar.toPath()).map {
            it.substringBefore(".class") to sideMarkers[it.substringBefore(".class")]
        }.filter { it.second != null }.map { it.first to it.second!! }
        if (type.size > 1) throw IllegalStateException("Found more than one side marker in forge jar: $type")
        if (type.isEmpty()) {
            project.logger.warn("No side marker found in forge jar, using default (none)")
            return@lazy null
        }
        type.first()
    }

    private fun applyAnnotationVisitor(visitor: AnnotationVisitor, env: EnvType) {
        if (actualSideMarker == null) return
        visitor.visitEnum(actualSideMarker!!.second.second, "L${actualSideMarker!!.second.first};", actualSideMarker!!.second.third[env])
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

    init {
        project.unimined.events.register(::applyToTask)
    }

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

    override fun at2aw(input: String, output: String, namespace: MappingNamespace) = at2aw(File(input), File(output), namespace)
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
    override fun at2aw(input: File, output: File, namespace: MappingNamespace) : File {
        return AccessTransformerMinecraftTransformer.at2aw(input.toPath(), output.toPath(), namespace.namespace, project.mappings.getMappingTree(EnvType.COMBINED), project.logger).toFile()
    }

    @get:ApiStatus.Internal
    val srgToMCPAsSRG: Path by lazy {
        project.unimined.getLocalCache().resolve("mappings").createDirectories().resolve("srg2mcp.srg").apply {
            val export = MappingExportImpl(EnvType.COMBINED).apply {
                location = toFile()
                type = MappingExportTypes.SRG
                sourceNamespace = MappingNamespace.SEARGE
                targetNamespace = listOf(devNamespace)
            }
            export.validate()
            export.exportFunc(project.mappings.getMappingTree(EnvType.COMBINED))
        }
    }

    @get:ApiStatus.Internal
    val srgToMCPAsMCP: Path by lazy {
        project.unimined.getLocalCache().resolve("mappings").createDirectories().resolve("srg2mcp.jar").apply {
            val export = MappingExportImpl(EnvType.COMBINED).apply {
                location = toFile()
                type = MappingExportTypes.MCP
                sourceNamespace = MappingNamespace.SEARGE
                skipComments = true // the reader forge uses now is too dumb...
                targetNamespace = listOf(devNamespace)
            }
            export.validate()
            export.exportFunc(project.mappings.getMappingTree(EnvType.COMBINED))
        }
    }

    init {
        project.repositories.maven {
            it.url = URI("https://maven.minecraftforge.net/")
            it.metadataSources {
                it.artifact()
            }
        }
    }

    override fun afterEvaluate() {

        if (forge.dependencies.isEmpty()) {
            throw IllegalStateException("No forge dependency found!")
        }

        val forgeDep = forge.dependencies.last()
        if (forgeDep.group != "net.minecraftforge" || !(forgeDep.name == "minecraftforge" || forgeDep.name == "forge")) {
            throw IllegalStateException("Invalid forge dependency found, if you are using multiple dependencies in the forge configuration, make sure the last one is the forge dependency!")
        }

        // test if pre unified jar
        if (provider.minecraft.mcVersionCompare(provider.minecraft.version, "1.3") < 0) {
            forgeTransformer = FG1MinecraftTransformer(project, this)
            // add forge client/server if universal is disabled
            val forgeClient = "${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:client@zip"
            val forgeServer = "${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:server@zip"
            forgeTransformer.jarModConfiguration(EnvType.CLIENT).dependencies.apply {
                add(project.dependencies.create(forgeClient))
            }
            forgeTransformer.jarModConfiguration(EnvType.SERVER).dependencies.apply {
                add(project.dependencies.create(forgeServer))
            }
        } else {
            forge.dependencies.remove(forgeDep)

            val zip = provider.minecraft.mcVersionCompare(provider.minecraft.version, "1.6") < 0
            val forgeUniversal = project.dependencies.create("${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:universal@${if (zip) "zip" else "jar"}")
            forge.dependencies.add(forgeUniversal)

            val jar = forge.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }
            forgeTransformer = determineForgeProviderFromUniversal(jar)

            //parse version json from universal jar and apply
            ZipReader.readInputStreamFor("version.json", jar.toPath(), false) {
                JsonParser.parseReader(InputStreamReader(it)).asJsonObject
            }?.let { versionJson ->
                val libraries = parseAllLibraries(versionJson.getAsJsonArray("libraries"))
                mainClass = versionJson.get("mainClass").asString
                val args = versionJson.get("minecraftArguments").asString
                provider.addMcLibraries(libraries.filter {
                    !it.name.startsWith("net.minecraftforge:minecraftforge:") && !it.name.startsWith(
                        "net.minecraftforge:forge:"
                    )
                })
                tweakClassClient = args.split("--tweakClass")[1].trim()
            }
        }

        forgeTransformer.afterEvaluate()
        super.afterEvaluate()
    }

    private fun determineForgeProviderFromUniversal(universal: File): JarModMinecraftTransformer {
        val files = mutableSetOf<ForgeFiles>()
        ZipReader.forEachInZip(universal.toPath()) { path, _ ->
            if (ForgeFiles.ffMap.contains(path)) {
                files.add(ForgeFiles.ffMap[path]!!)
            }
        }

        var forgeTransformer: JarModMinecraftTransformer? = null
        for (vers in ForgeVersion.values()) {
            if (files.containsAll(vers.accept) && files.none { it in vers.deny }) {
                project.logger.debug("Files $files")
                forgeTransformer = when (vers) {
                    ForgeVersion.FG1 -> {
                        project.logger.lifecycle("Selected FG1")
                        FG1MinecraftTransformer(project, this)
                    }

                    ForgeVersion.FG2 -> {
                        project.logger.lifecycle("Selected FG2")
                        FG2MinecraftTransformer(project, this)
                    }

                    ForgeVersion.FG3 -> {
                        project.logger.lifecycle("Selected FG3")
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
        project.logger.lifecycle("Applying ATs $ats")
        return if (accessTransformer != null) {
            project.logger.lifecycle("Using user access transformer $accessTransformer")
            val output = MinecraftJar(
                baseMinecraft,
                parentPath = project.unimined.getLocalCache().resolve("forge"),
                awOrAt = "at+${accessTransformer!!.toPath().getSha1()}"
            )
            if (!output.path.exists() || project.gradle.startParameter.isRefreshDependencies) {
                AccessTransformerMinecraftTransformer.transform(
                    ats + listOf(accessTransformer!!.toPath()),
                    baseMinecraft,
                    output
                )
            }
            output
        } else {
            val output = MinecraftJar(baseMinecraft, awOrAt = "at")
            if (!output.path.exists() || project.gradle.startParameter.isRefreshDependencies) {
                AccessTransformerMinecraftTransformer.transform(ats, baseMinecraft, output)
            }
            output
        }
    }

    override fun applyLaunches() {
        super.applyLaunches()
        //TODO: figure out datagen
    }

    override fun applyClientRunTransform(config: LaunchConfig) {
        project.logger.info("Adding mixin config $mixinConfig to client run config")
        forgeTransformer.applyClientRunTransform(config)
        for (mixin in mixinConfig) {
            config.args += listOf("--mixin", mixin)
        }
    }

    override fun applyServerRunTransform(config: LaunchConfig) {
        project.logger.info("Adding mixin config $mixinConfig to server run config")
        forgeTransformer.applyServerRunTransform(config)
        for (mixin in mixinConfig) {
            config.args += listOf("--mixin", mixin)
        }
    }

    override fun afterRemapJarTask(output: Path) {
        //TODO: JarJar
    }

    private fun applyToTask(container: TaskContainer) {
        for (jar in container.withType(Jar::class.java)) {
            jar.manifest {
                it.attributes["MixinConfigs"] = mixinConfig.joinToString(",")
            }
        }
    }

    override fun sourceSets(sourceSets: SourceSetContainer) {
        forgeTransformer.sourceSets(sourceSets)
    }

}