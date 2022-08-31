package xyz.wagyourtail.unimined.providers.minecraft.patch.forge

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.SemVerUtils
import xyz.wagyourtail.unimined.deleteRecursively
import xyz.wagyourtail.unimined.maybeCreate
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.patch.forge.fg2.FG2TaskApplyBinPatches
import xyz.wagyourtail.unimined.providers.minecraft.patch.jarmod.JarModMinecraftTransformer
import java.io.File
import java.net.URI
import java.nio.file.*
import kotlin.io.path.*

class FG2MinecraftTransformer(project: Project, val parent: ForgeMinecraftTransformer) : JarModMinecraftTransformer(
    project,
    parent.provider,
    Constants.FORGE_PROVIDER
) {

    val forge = jarModConfiguration(EnvType.COMBINED)
    private val atMappings = "official"

    init {
        project.repositories.maven {
            it.url = URI("https://maven.minecraftforge.net/")
            it.metadataSources {
                it.artifact()
            }
        }
    }
    override fun afterEvaluate() {
        // get and add forge-src to mappings
        val forgeDep = forge.dependencies.last()

        val forgeSrc = "${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:src@zip"
        provider.parent.mappingsProvider.getMappings(EnvType.COMBINED).dependencies.apply {
            if (isEmpty()) {
                if (SemVerUtils.matches(provider.minecraftDownloader.version, "<1.7")) {
                    add(project.dependencies.create(forgeSrc))
                } else if (SemVerUtils.matches(provider.minecraftDownloader.version, "<1.7.10")) {
                    throw UnsupportedOperationException("Forge 1.7-1.7.9 don't have automatic mappings support. please supply the mcp mappings or whatever manually")
                } else {
                    if (parent.mcpVersion == null || parent.mcpChannel == null) throw IllegalStateException("mcpVersion and mcpChannel must be set in forge block for 1.7+")
                    add(project.dependencies.create("de.oceanlabs.mcp:mcp:${provider.minecraftDownloader.version}:srg@zip"))
                    add(project.dependencies.create("de.oceanlabs.mcp:mcp_${parent.mcpChannel}:${parent.mcpVersion}@zip"))
                }
            }
        }

        super.afterEvaluate()
    }

    fun Configuration.getFile(dep: Dependency, extension: String = "jar"): File {
        resolve()
        return files(dep).first { it.extension == extension }
    }

    override fun transform(envType: EnvType, baseMinecraft: Path): Path {
        val forgeUniversal = forge.dependencies.last()
        val forgeJar = forge.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }

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

        val accessModder = AccessTransformerMinecraftTransformer(project, provider).apply {
            parent.accessTransformer?.let { addAccessTransformer(it) }
            ZipReader.readInputStreamFor("fml_at.cfg", forgeJar.toPath(), false) {
                addAccessTransformer(it)
            }
            ZipReader.readInputStreamFor("forge_at.cfg", forgeJar.toPath(), false) {
                addAccessTransformer(it)
            }
        }

        val atOutOfficial = if (atMappings != "official") {
            val patchedMCSrg = provider.mcRemapper.provide(envType, patchedMC, "searge", "official", true)
            val atsOut = accessModder.transform(envType, patchedMCSrg)
            provider.mcRemapper.provide(envType, atsOut, "official", "searge", true)
        } else {
            accessModder.transform(envType, patchedMC)
        }

        //TODO remap forge separately
        return super.transform(envType, atOutOfficial)
    }

    override fun applyClientRunConfig(tasks: TaskContainer) {
        provider.provideRunClientTask(tasks) {
            it.jvmArgs += "-Dfml.ignoreInvalidMinecraftCertificates=true"
            it.jvmArgs += "-Dnet.minecraftforge.gradle.GradleStart.srg.srg-mcp=${parent.srgToMcpMappings}"
            it.args += "--tweakClass ${parent.tweakClass ?: "net.minecraftforge.fml.common.launcher.FMLTweaker"}"
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
                    out.getPath("argo").apply {
                        if (exists()) {
                            deleteRecursively()
                        }
                    }
                    out.getPath("org/bouncycastle").apply {
                        if (exists()) {
                            deleteRecursively()
                        }
                    }
                    out.getPath("com/google").apply {
                        if (exists()) {
                            deleteRecursively()
                        }
                    }
                    out.getPath("org/apache").apply {
                        if (exists()) {
                            deleteRecursively()
                        }
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