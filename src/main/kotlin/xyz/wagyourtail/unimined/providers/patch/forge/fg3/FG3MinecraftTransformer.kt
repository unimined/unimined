package xyz.wagyourtail.unimined.providers.patch.forge.fg3

import com.google.gson.JsonParser
import net.fabricmc.mappingio.format.ZipReader
import net.minecraftforge.binarypatcher.ConsoleTool
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.*
import xyz.wagyourtail.unimined.providers.RunConfig
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.patch.MinecraftJar
import xyz.wagyourtail.unimined.providers.patch.forge.ForgeMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.forge.fg3.mcpconfig.McpConfigData
import xyz.wagyourtail.unimined.providers.patch.forge.fg3.mcpconfig.McpConfigStep
import xyz.wagyourtail.unimined.providers.patch.forge.fg3.mcpconfig.McpExecutor
import xyz.wagyourtail.unimined.providers.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.util.consumerApply
import xyz.wagyourtail.unimined.util.getFile
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.*
import kotlin.io.path.*

class FG3MinecraftTransformer(project: Project, val parent: ForgeMinecraftTransformer) : JarModMinecraftTransformer(
    project,
    parent.provider,
    Constants.FORGE_PROVIDER
) {

    @ApiStatus.Internal
    val forgeUd = project.configurations.maybeCreate(Constants.FORGE_USERDEV)

    @ApiStatus.Internal
    val clientExtra = project.configurations.maybeCreate(Constants.FORGE_CLIENT_EXTRA)

    @ApiStatus.Internal
    val forgeInstaller = project.configurations.maybeCreate(Constants.FORGE_INSTALLER)

    lateinit var mcpConfig: Dependency
    val mcpConfigData by lazy {
        val config = provider.parent.mappingsProvider.getMappings(EnvType.COMBINED).getFile(mcpConfig, Regex("zip"))
        val configJson = ZipReader.readInputStreamFor("config.json", config.toPath()) {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }
        McpConfigData.fromJson(configJson)
    }

    override fun afterEvaluate() {
        val forgeDep = parent.forge.dependencies.last()

        // detect if userdev3 or userdev
        //   read if forgeDep has binpatches file
        val forgeUni = parent.forge.getFile(forgeDep)
        val userdevClassifier = ZipReader.readInputStreamFor<String?>(
            "binpatches.pack.lzma",
            forgeUni.toPath(),
            false
        ) {
            "userdev3"
        } ?: "userdev"

        val userdev = "${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:$userdevClassifier"
        forgeUd.dependencies.add(project.dependencies.create(userdev))

//        val installer = "${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:installer"
//        forgeInstaller.dependencies.add(project.dependencies.create(installer))

        provider.parent.mappingsProvider.getMappings(EnvType.COMBINED).dependencies.apply {
            val empty = isEmpty()
            mcpConfig = project.dependencies.create("de.oceanlabs.mcp:mcp_config:${provider.minecraftDownloader.version}@zip")
            add(mcpConfig)
            if (empty) {
                if (parent.mcpVersion == null || parent.mcpChannel == null) throw IllegalStateException("mcpVersion and mcpChannel must be set in forge block for 1.7+")
                add(project.dependencies.create("de.oceanlabs.mcp:mcp_${parent.mcpChannel}:${parent.mcpVersion}@zip"))
            } else {
                val deps = this.filter { it != mcpConfig }
                clear()
                add(mcpConfig)
                addAll(deps)
            }
        }

        for (element in userdevCfg.get("libraries")?.asJsonArray ?: listOf()) {
            if (element.asString.contains("legacydev")) continue
            val dep = element.asString.split(":")
            if (dep[1] == "gson" && dep[2] == "2.8.0") {
                provider.mcLibraries.dependencies.add(project.dependencies.create("${dep[0]}:${dep[1]}:2.8.9"))
            } else {
                provider.mcLibraries.dependencies.add(project.dependencies.create(element.asString.replace(".+", "")))
            }
        }

        // get forge userdev jar
        val forgeUd = forgeUd.getFile(forgeUd.dependencies.last())
        if (userdevCfg.has("inject")) {
            project.logger.warn("injecting forge userdev into minecraft jar")
            this.addTransform { outputJar ->
                ZipReader.openZipFileSystem(forgeUd.toPath()).use { inputJar ->
                    val inject = inputJar.getPath("inject")
                    if (Files.exists(inject)) {
                        project.logger.warn("injecting forge userdev into minecraft jar")
                        Files.walk(inject).forEach { path ->
                            project.logger.warn("testing $path")
                            if (!Files.isDirectory(path)) {
                                val target = outputJar.getPath("/${inject.relativize(path)}")
                                project.logger.warn("injecting $path into minecraft jar")
                                Files.createDirectories(target.parent)
                                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                    }
                }
            }
        }

        userdevCfg.get("runs").asJsonObject.get("client").asJsonObject.apply {
            val mainClass = get("main").asString
            if (!mainClass.startsWith("net.minecraftforge.legacydev")) {
                project.logger.warn("inserting mcp mappings")
                provider.mcLibraries.dependencies.add(
                    project.dependencies.create(project.files(parent.srgToMCPAsMCP))
                )
            }
        }

        super.afterEvaluate()
    }

    val userdevCfg by lazy {
        // get forge userdev jar
        val forgeUd = forgeUd.getFile(forgeUd.dependencies.last())

        ZipReader.readInputStreamFor("config.json", forgeUd.toPath()) {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }!!
    }

    @Throws(IOException::class)
    private fun executeMcp(step: String, outputPath: Path, envType: EnvType) {
        val type = when (envType) {
            EnvType.CLIENT -> "client"
            EnvType.SERVER -> "server"
            EnvType.COMBINED -> "joined"
        }
        val steps: List<McpConfigStep> = mcpConfigData.steps.get(type)!!
        val executor = McpExecutor(
            project,
            provider,
            outputPath.parent.resolve("mcpconfig").createDirectories(),
            steps,
            mcpConfigData.functions
        )
        val output: Path = executor.executeUpTo(step)
        Files.copy(output, outputPath, StandardCopyOption.REPLACE_EXISTING)
    }

    override fun sourceSets(sourceSets: SourceSetContainer) {
        sourceSets.getByName("main").apply {
            compileClasspath += clientExtra
            runtimeClasspath += clientExtra
        }
        sourceSets.findByName("client")?.apply {
            compileClasspath += clientExtra
            runtimeClasspath += clientExtra
        }
        sourceSets.findByName("server")?.apply {
            compileClasspath += clientExtra
            runtimeClasspath += clientExtra
        }
    }

    override fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar, output: Path): MinecraftJar {
        project.logger.warn("Merging client and server jars...")
        unstripResources(clientjar, serverjar, output)
        return if (userdevCfg["notchObf"]?.asBoolean == true) {
            if (output.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                MinecraftJar(clientjar, output, EnvType.COMBINED)
            } else {
                executeMcp("merge", output, EnvType.COMBINED)
                MinecraftJar(clientjar, output, EnvType.COMBINED)
            }
        } else {
            if (output.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                MinecraftJar(clientjar, output, EnvType.COMBINED, "searge")
            } else {
                executeMcp("rename", output, EnvType.COMBINED)
                MinecraftJar(clientjar, output, EnvType.COMBINED, "searge")
            }

        }
    }

    private fun unstripResources(
        baseMinecraftClient: MinecraftJar,
        baseMinecraftServer: MinecraftJar,
        patchedMinecraft: Path
    ) {
//        val unstripped = patchedMinecraft.jarPath.parent.resolve("${patchedMinecraft.jarPath.nameWithoutExtension}-unstripped.jar")
//        patchedMinecraft.jarPath.copyTo(unstripped, StandardCopyOption.REPLACE_EXISTING)
        val clientExtra = patchedMinecraft.parent.createDirectories()
            .resolve("client-extra-${provider.minecraftDownloader.version}.jar")

        this.clientExtra.dependencies.add(
            project.dependencies.create(
                project.files(clientExtra.toString())
            )
        )

        if (clientExtra.exists()) {
            if (project.gradle.startParameter.isRefreshDependencies) {
                clientExtra.deleteExisting()
            } else {
                return
            }
        }

        ZipReader.openZipFileSystem(clientExtra, mapOf("mutable" to true, "create" to true)).use { unstripped ->
            ZipReader.openZipFileSystem(baseMinecraftClient.jarPath).use { base ->
                unstrip(base, unstripped)
            }
        }
    }

    private fun unstrip(inp: FileSystem, out: FileSystem) {
        for (path in Files.walk(inp.getPath("/"))) {
            // skip meta-inf
            if (path.nameCount > 0 && path.getName(0).toString().equals("META-INF", ignoreCase = true)) continue
//            project.logger.warn("Checking $path")
            if (!path.isDirectory() && path.extension != "class") {
//                project.logger.warn("Copying $path")
                val target = out.getPath(path.toString())
                target.parent.createDirectories()
                path.copyTo(target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        project.logger.warn("transforming minecraft jar $minecraft for FG3")
        val patchedMC = minecraft.let(consumerApply {
            val forgeUniversal = parent.forge.dependencies.last()
            val forgeUd = forgeUd.getFile(forgeUd.dependencies.last())

            // get forge jar
            val forge = parent.forge.getFile(forgeUniversal)

            val outFolder = minecraft.jarPath.parent.resolve("${forgeUniversal.name}-${forgeUniversal.version}")
                .createDirectories()

            // if userdev cfg says notch
            if (userdevCfg["notchObf"]?.asBoolean == true && envType != EnvType.COMBINED) {
                throw IllegalStateException("Forge userdev3 (legacy fg3, aka 1.12.2) is not supported for non-combined environments.")
            }

            //   apply binpatches
            val binPatchFile = ZipReader.readInputStreamFor(userdevCfg["binpatches"].asString, forgeUd.toPath()) {
                outFolder.resolve("binpatches.pack.lzma")
                    .apply {
                        writeBytes(
                            it.readBytes(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING
                        )
                    }
            }

            val patchedMC = outFolder.resolve(
                "${
                    jarPath.nameWithoutExtension.replace(
                        "minecraft",
                        "forge"
                    )
                }-${forgeUniversal.name}-${forgeUniversal.version}.${jarPath.extension}"
            )
            if (!patchedMC.exists() || project.gradle.startParameter.isRefreshDependencies) {
                patchedMC.deleteIfExists()
                val args = (userdevCfg["binpatcher"].asJsonObject["args"].asJsonArray.map {
                    when (it.asString) {
                        "{clean}" -> jarPath.toString()
                        "{patch}" -> binPatchFile.toString()
                        "{output}" -> patchedMC.toString()
                        else -> it.asString
                    }
                } + listOf("--data", "--unpatched")).toTypedArray()
                try {
                    ConsoleTool.main(args)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    patchedMC.deleteIfExists()
                    throw e
                }
            }
            MinecraftJar(this, patchedMC)
        })

        //   shade in forge jar
        return super.transform(patchedMC)
    }

    val legacyClasspath = provider.parent.getLocalCache().createDirectories().resolve("legacy_classpath.txt")

    private fun getArgValue(config: RunConfig, arg: String): String {
        if (arg.startsWith("{")) {
            return when (arg) {
                "{minecraft_classpath_file}" -> {
                    legacyClasspath.toString()
                }

                "{modules}" -> {
                    val libs = mapOf(*provider.mcLibraries.dependencies.map { it.group + ":" + it.name + ":" + it.version to it }
                        .toTypedArray())
                    userdevCfg.get("modules").asJsonArray.joinToString(File.pathSeparator) {
                        val dep = libs[it.asString]
                            ?: throw IllegalStateException("Module ${it.asString} not found in mc libraries")
                        provider.mcLibraries.getFile(dep).toString()
                    }
                }

                "{assets_root}" -> {
                    val assetsDir = provider.minecraftDownloader.metadata.assetIndex?.let {
                        provider.assetsDownloader.downloadAssets(project, it)
                    }
                    (assetsDir ?: provider.clientWorkingDirectory.get().resolve("assets").toPath()).toString()
                }

                "{asset_index}" -> provider.minecraftDownloader.metadata.assetIndex?.id ?: ""
                "{source_roots}" -> {
                    (listOf(config.commonClasspath.output.resourcesDir) + config.commonClasspath.output.files).joinToString(
                        File.pathSeparator
                    ) { "mod%%$it" }
                }

                "{mcp_mappings}" -> "unimined.stub"
                "{natives}" -> {
                    val nativesDir = provider.clientWorkingDirectory.get().resolve("natives").toPath()
                    nativesDir.createDirectories()
                    nativesDir.toString()
                }

                else -> throw IllegalArgumentException("Unknown arg $arg")
            }
        } else {
            return arg
        }
    }

    private fun createLegacyClasspath() {
//        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
//        val source = sourceSets.findByName("client") ?: sourceSets.getByName("main")

        legacyClasspath.writeText(
            (provider.mcLibraries.resolve() + provider.combined.resolve() + provider.client.resolve() + clientExtra.resolve()).joinToString(
                "\n"
            ) { it.toString() },
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
        )
    }

    override fun applyClientRunConfig(tasks: TaskContainer) {
        createLegacyClasspath()
        userdevCfg.get("runs").asJsonObject.get("client").asJsonObject.apply {
            val mainClass = get("main").asString

            provider.overrideMainClassClient.set(mainClass)
            parent.tweakClass = get("env")?.asJsonObject?.get("tweakClass")?.asString
            if (mainClass.startsWith("net.minecraftforge.legacydev")) {
                provider.provideVanillaRunClientTask(tasks) {
                    it.mainClass = "net.minecraft.launchwrapper.Launch"
                    it.jvmArgs += "-Dfml.ignoreInvalidMinecraftCertificates=true"
                    it.jvmArgs += "-Dnet.minecraftforge.gradle.GradleStart.srg.srg-mcp=${parent.srgToMCPAsSRG}"
                    it.args += "--tweakClass ${parent.tweakClass ?: "net.minecraftforge.fml.common.launcher.FMLTweaker"}"
                }
            } else {
                val args = get("args")?.asJsonArray?.map { it.asString } ?: listOf()
                val jvmArgs = get("jvmArgs")?.asJsonArray?.map { it.asString } ?: listOf()
                val env = get("env")?.asJsonObject?.entrySet()?.associate { it.key to it.value.asString } ?: mapOf()
                val props = get("props")?.asJsonObject?.entrySet()?.associate { it.key to it.value.asString } ?: mapOf()
                provider.provideVanillaRunClientTask(tasks) { run ->
                    run.mainClass = mainClass
                    run.args.clear()
                    run.args += args.map { getArgValue(run, it) }
                    run.jvmArgs += jvmArgs.map { getArgValue(run, it) }
                    run.jvmArgs += props.map { "-D${it.key}=${getArgValue(run, it.value)}" }
                    run.env += mapOf("FORGE_SPEC" to userdevCfg.get("spec").asNumber.toString())
                    run.env += env.map { it.key to getArgValue(run, it.value) }
                }
            }
        }
    }

    override fun afterRemap(envType: EnvType, namespace: String, baseMinecraft: Path): Path {
        val out = fixForge(envType, namespace, baseMinecraft)
        ZipReader.openZipFileSystem(out).use { fs ->
            return parent.applyATs(
                out,
                listOf(
                    fs.getPath("fml_at.cfg"),
                    fs.getPath("forge_at.cfg"),
                    fs.getPath("META-INF/accesstransformer.cfg")
                ).filter { Files.exists(it) })
        }
    }

    fun fixForge(envType: EnvType, namespace: String, baseMinecraft: Path): Path {
        if (namespace == "named") {
            val target = baseMinecraft.parent.resolve("${baseMinecraft.nameWithoutExtension}-stripped.${baseMinecraft.extension}")

            if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                return target
            }

            Files.copy(baseMinecraft, target, StandardCopyOption.REPLACE_EXISTING)
            val mc = URI.create("jar:${target.toUri()}")
            try {
                FileSystems.newFileSystem(mc, mapOf("mutable" to true), null).use { out ->
                    out.getPath("binpatches.pack.lzma").deleteIfExists()

                    //TODO: FIXME, hack. remove forge trying to transform class names for fg2 dev launch
                    out.getPath("net/minecraftforge/fml/common/asm/transformers/DeobfuscationTransformer.class")
                        .deleteIfExists()
                }
            } catch (e: Throwable) {
                target.deleteExisting()
                throw e
            }
            return target
        }
        return baseMinecraft
    }
}