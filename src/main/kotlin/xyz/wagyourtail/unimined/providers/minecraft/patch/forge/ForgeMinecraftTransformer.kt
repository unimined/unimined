package xyz.wagyourtail.unimined.providers.minecraft.patch.forge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.SemVerUtils
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.version.parseAllLibraries
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Path

class ForgeMinecraftTransformer(project: Project, provider: MinecraftProvider) :
        AbstractMinecraftTransformer(project, provider) {

    val forge = project.configurations.maybeCreate(Constants.FORGE_PROVIDER)

    @ApiStatus.Internal
    lateinit var forgeTransformer: JarModMinecraftTransformer

    var accessTransformer: File? = null
    var mcpVersion: String? = null
    var mcpChannel: String? = null

    @ApiStatus.Internal
    var tweakClass: String? = null

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

        forge.dependencies.remove(forgeDep)

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
                project.logger.info("Files $files")
                forgeTransformer = when(vers) {
                    ForgeVersion.FG1 -> {
                        project.logger.info("Selected FG1")
                        FG1MinecraftTransformer(project, this)
                    }
                    ForgeVersion.FG2 -> {
                        project.logger.info("Selected FG2")
                        FG2MinecraftTransformer(project, this)
                    }
                    ForgeVersion.FG3 -> {
                        project.logger.info("Selected FG3")
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

    override fun transform(envType: EnvType, baseMinecraft: Path): Path {
        return forgeTransformer.transform(envType, baseMinecraft)
    }

    enum class ForgeFiles(val path: String) {
        FORGE_AT("META-INF/accesstransformer.cfg"),
        OLD_FORGE_AT("forge_at.cfg"),
        BINPATCHES_PACK("binpatches.pack.lzma"),
        JAR_PATCHES("net/minecraft/client/Minecraft.class"),
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
                ForgeFiles.BINPATCHES_PACK
            )
        ),
        FG2(
            setOf(
                ForgeFiles.OLD_FORGE_AT,
                ForgeFiles.BINPATCHES_PACK
            ),
            setOf(
                ForgeFiles.JAR_PATCHES,
                ForgeFiles.FORGE_AT
            )
        ),
        FG3(
            setOf(
                ForgeFiles.FORGE_AT,
            ),
            setOf(
                ForgeFiles.BINPATCHES_PACK,
                ForgeFiles.JAR_PATCHES,
                ForgeFiles.OLD_FORGE_AT
            )
        ),
    }

}