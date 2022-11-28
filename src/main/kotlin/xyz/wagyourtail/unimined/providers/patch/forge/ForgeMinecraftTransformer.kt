package xyz.wagyourtail.unimined.providers.patch.forge

import com.google.gson.JsonParser
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.providers.MinecraftProvider
import xyz.wagyourtail.unimined.providers.mappings.MappingExport
import xyz.wagyourtail.unimined.providers.mappings.MappingExportTypes
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.MinecraftJar
import xyz.wagyourtail.unimined.providers.patch.forge.fg1.FG1MinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.forge.fg2.FG2MinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.forge.fg3.FG3MinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.providers.version.parseAllLibraries
import xyz.wagyourtail.unimined.remap.RemapJarTask
import xyz.wagyourtail.unimined.util.getSha1
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class ForgeMinecraftTransformer(project: Project, provider: MinecraftProvider) :
        AbstractMinecraftTransformer(project, provider) {

    val forge = project.configurations.maybeCreate(Constants.FORGE_PROVIDER)

    @ApiStatus.Internal
    lateinit var forgeTransformer: JarModMinecraftTransformer

    var accessTransformer: File? = null
    var mcpVersion: String? = null
    var mcpChannel: String? = null

    var includeSubprojectSourceSets = mutableSetOf<SourceSet>()

    @ApiStatus.Internal
    var tweakClass: String? = null

    fun aw2at(input: String): File = aw2at(File(input))

    fun aw2at(input: String, output: String) = aw2at(File(input), File(output))

    fun aw2at(input: File) = aw2at(input, project.extensions.getByType(SourceSetContainer::class.java).getByName("main").resources.srcDirs.first().resolve("META-INF/accesstransformer.cfg"))

    fun aw2at(input: File, output: File): File {
        return AccessTransformerMinecraftTransformer.aw2at(input.toPath(), output.toPath()).toFile()
    }

    fun aw2atLegacy(input: String): File = aw2atLegacy(File(input))

    fun aw2atLegacy(input: String, output: String) = aw2atLegacy(File(input), File(output))

    fun aw2atLegacy(input: File) = aw2atLegacy(input, project.extensions.getByType(SourceSetContainer::class.java).getByName("main").resources.srcDirs.first().resolve("META-INF/accesstransformer.cfg"))

    fun aw2atLegacy(input: File, output: File): File {
        return AccessTransformerMinecraftTransformer.aw2at(input.toPath(), output.toPath(), true).toFile()
    }

    @get:ApiStatus.Internal
    val srgToMCPAsSRG: Path by lazy {
        provider.parent.getLocalCache().resolve("mappings").createDirectories().resolve("srg2mcp.srg").apply {
            val export = MappingExport(EnvType.COMBINED).apply {
                location = toFile()
                type = MappingExportTypes.SRG
                sourceNamespace = "searge"
                targetNamespace = listOf("named")
            }
            export.validate()
            export.exportFunc(this@ForgeMinecraftTransformer.provider.parent.mappingsProvider.getMappingTree(EnvType.COMBINED))
        }
    }

    @get:ApiStatus.Internal
    val srgToMCPAsMCP: Path by lazy {
        provider.parent.getLocalCache().resolve("mappings").createDirectories().resolve("srg2mcp.jar").apply {
            val export = MappingExport(EnvType.COMBINED).apply {
                location = toFile()
                type = MappingExportTypes.MCP
                sourceNamespace = "searge"
                targetNamespace = listOf("named")
            }
            export.validate()
            export.exportFunc(this@ForgeMinecraftTransformer.provider.parent.mappingsProvider.getMappingTree(EnvType.COMBINED))
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
        if (provider.minecraftDownloader.mcVersionCompare(provider.minecraftDownloader.version, "1.3") < 0) {
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

            val zip = provider.minecraftDownloader.mcVersionCompare(provider.minecraftDownloader.version, "1.6") < 0
            val forgeUniversal = project.dependencies.create("${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:universal@${if (zip) "zip" else "jar"}")
            forge.dependencies.add(forgeUniversal)

            val jar = forge.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }
            forgeTransformer = determineForgeProviderFromUniversal(jar)

            //parse version json from universal jar and apply
            ZipReader.readInputStreamFor("version.json", jar.toPath(), false) {
                JsonParser.parseReader(InputStreamReader(it)).asJsonObject
            }?.let { versionJson ->
                val libraries = parseAllLibraries(versionJson.getAsJsonArray("libraries"))
                val mainClass = versionJson.get("mainClass").asString
                val args = versionJson.get("minecraftArguments").asString
                provider.overrideMainClassClient.set(mainClass)
                provider.addMcLibraries(libraries.filter {
                    !it.name.startsWith("net.minecraftforge:minecraftforge:") && !it.name.startsWith(
                        "net.minecraftforge:forge:"
                    )
                })
                tweakClass = args.split("--tweakClass")[1].trim()
            }
        }

        forgeTransformer.afterEvaluate()

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
                parentPath = provider.parent.getLocalCache().resolve("forge"),
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

    override fun applyClientRunConfig(tasks: TaskContainer) {

    }

    override fun applyServerRunConfig(tasks: TaskContainer) {

    }

    override fun afterRemapJarTask(task: RemapJarTask, output: Path) {

    }

}