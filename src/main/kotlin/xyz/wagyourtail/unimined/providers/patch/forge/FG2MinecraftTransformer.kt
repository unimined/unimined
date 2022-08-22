package xyz.wagyourtail.unimined.providers.patch.forge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.SemVerUtils
import xyz.wagyourtail.unimined.deleteRecursively
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.version.parseAllLibraries
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.jarmod.JarModMinecraftTransformer
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class FG2MinecraftTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(
    project,
    provider
) {

    val forgeJarModder = JarModMinecraftTransformer(project, provider, Constants.FORGE_PROVIDER)
    val accessTransformer: File? = null

    override fun afterEvaluate() {
        // get and add forge-src to mappings
        val forge = forgeJarModder.jarModConfiguration(EnvType.COMBINED).dependencies.last()

        if (SemVerUtils.matches(provider.minecraftDownloader.version, "<1.7")) {
            val forgeSrc = "${forge.group}:${forge.name}:${forge.version}:src@zip"
            provider.mcRemapper.getMappings(EnvType.COMBINED).dependencies.apply {
                clear()
                add(project.dependencies.create(forgeSrc))
            }
        }

        // replace forge with universal
        val forgeUniversal = project.dependencies.create("${forge.group}:${forge.name}:${forge.version}:universal")
        forgeJarModder.jarModConfiguration(EnvType.COMBINED).dependencies.apply {
            remove(forge)
            add(forgeUniversal)
        }

        //parse version json from universal jar and apply
        val forgeJar = forgeJarModder.jarModConfiguration(EnvType.COMBINED).apply {
            resolve()
        }.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }

        val versionJson: JsonObject = ZipReader.readInputStreamFor("version.json", forgeJar.toPath()) {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }

        val libraries = parseAllLibraries(versionJson.getAsJsonArray("libraries"))
        val mainClass = versionJson.get("mainClass").asString
        provider.overrideMainClassClient.set(mainClass)
        provider.addMcLibraries(libraries)

        super.afterEvaluate()
    }

    override fun transform(envType: EnvType, baseMinecraft: Path): Path {
        TODO()
    }

    override fun afterRemap(envType: EnvType, namespace: String, baseMinecraft: Path): Path {
        if (namespace == "named") {
            val target = baseMinecraft.parent.resolve("${baseMinecraft.nameWithoutExtension}-stripped.${baseMinecraft.extension}")

            if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                return target
            }

            Files.copy(baseMinecraft, target, StandardCopyOption.REPLACE_EXISTING)
            val mc = URI.create("jar:${target.toUri()}")
            try {
                FileSystems.newFileSystem(mc, mapOf("mutable" to true), null).use { out ->
                    if (out.getPath("argo").exists()) {
                        out.getPath("argo").deleteRecursively()
                    }
                    if (out.getPath("org/bouncycastle").exists()) {
                        out.getPath("org/bouncycastle").deleteRecursively()
                    }
                    if (out.getPath("com/google").exists()) {
                        out.getPath("com/google").deleteRecursively()
                    }
                    if (out.getPath("org/apache").exists()) {
                        out.getPath("org/apache").deleteRecursively()
                    }
                    if (out.getPath("binpatches.pack.lzma").exists()) {
                        out.getPath("binpatches.pack.lzma").deleteExisting()
                    }
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