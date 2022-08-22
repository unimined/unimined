package xyz.wagyourtail.unimined.providers.patch.forge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import xyz.wagyourtail.unimined.providers.patch.forge.fg2.FG2TaskApplyBinPatches
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.*
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
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

class FG2MinecraftTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(
    project,
    provider
) {

    val forge = project.configurations.maybeCreate(Constants.FORGE_PROVIDER)
    val accessTransformer: File? = null
    private lateinit var forgeUniversal: Dependency
    private lateinit var tweakClass: String
    override fun afterEvaluate() {
        // get and add forge-src to mappings
        val forgeDep = forge.dependencies.last()

        if (SemVerUtils.matches(provider.minecraftDownloader.version, "<1.7")) {
            val forgeSrc = "${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:src@zip"
            provider.mcRemapper.getMappings(EnvType.COMBINED).dependencies.apply {
                clear()
                add(project.dependencies.create(forgeSrc))
            }
        }

        // replace forge with universal
        forgeUniversal = project.dependencies.create("${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:universal")
        forge.dependencies.apply {
            remove(forgeDep)
            add(forgeUniversal)
        }

        //parse version json from universal jar and apply
        val forgeJar = forge.apply {
            resolve()
        }.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }

        val versionJson: JsonObject = ZipReader.readInputStreamFor("version.json", forgeJar.toPath()) {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }

        val libraries = parseAllLibraries(versionJson.getAsJsonArray("libraries"))
        val mainClass = versionJson.get("mainClass").asString
        val args = versionJson.get("minecraftArguments").asString
        provider.overrideMainClassClient.set(mainClass)
        provider.addMcLibraries(libraries.filter { !it.name.startsWith("net.minecraftforge:minecraftforge:") && !it.name.startsWith("net.minecraftforge:forge:") })
        tweakClass = args.split("--tweakClass")[1].trim()
        super.afterEvaluate()
    }

    fun Configuration.getFile(dep: Dependency, extension: String = "jar"): File {
        resolve()
        return files(dep).first { it.extension == extension }
    }

    override fun transform(envType: EnvType, baseMinecraft: Path): Path {
        val forgeJar = forge.apply {
            resolve()
        }.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }

        val outFolder = baseMinecraft.parent.resolve("${forgeUniversal.name}-${forgeUniversal.version}").maybeCreate()

        // apply binary patches
        val binaryPatchFile = ZipReader.readInputStreamFor("binpatches.pack.lzma", forgeJar.toPath()) {
            outFolder.resolve("binpatches.pack.lzma").apply { writeBytes(it.readBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING) }
        }
        val patchedMC = outFolder.resolve("${baseMinecraft.nameWithoutExtension}-${forgeUniversal.name}-${forgeUniversal.version}.${baseMinecraft.extension}")
        if (!patchedMC.exists() || project.gradle.startParameter.isRefreshDependencies) {
            patchedMC.deleteIfExists()
            FG2TaskApplyBinPatches(project).doTask(baseMinecraft.toFile(), binaryPatchFile.toFile(), patchedMC.toFile(), if (envType == EnvType.SERVER) "server" else "client")
        }
        val accessModder = AccessTransformerMinecraftTransformer(project, provider)
        if (accessTransformer != null) {
            accessModder.addAccessTransformer(accessTransformer)
        }

        ZipReader.readInputStreamFor("fml_at.cfg", forgeJar.toPath(), false) {
            accessModder.addAccessTransformer(it)
        }
        ZipReader.readInputStreamFor("forge_at.cfg", forgeJar.toPath(), false) {
            accessModder.addAccessTransformer(it)
        }

        val patchedMCSrg = provider.mcRemapper.provide(envType, patchedMC, "searge", "official", true)
        val atsOut = accessModder.transform(envType, patchedMCSrg)
        val atOutOfficial = provider.mcRemapper.provide(envType, atsOut, "official", "searge", true)

        //TODO remap forge separately
        val jarmodder = JarModMinecraftTransformer(project, provider, Constants.FORGE_PROVIDER)
        return jarmodder.transform(envType, atOutOfficial)
    }

    override fun applyClientRunConfig(tasks: TaskContainer) {
        provider.provideRunClientTask(tasks) {
            it.jvmArgs += "-Dfml.ignoreInvalidMinecraftCertificates=true"
            it.args += "--tweakClass $tweakClass"
        }
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

                    out.getPath("binpatches.pack.lzma").deleteIfExists()

                    //TODO: FIXME, hack. remove forge trying to transform class names for fg2 dev launch
                    out.getPath("net/minecraftforge/fml/common/asm/transformers/DeobfuscationTransformer.class").deleteIfExists()
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