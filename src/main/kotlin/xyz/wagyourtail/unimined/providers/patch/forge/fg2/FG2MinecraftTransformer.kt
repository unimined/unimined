package xyz.wagyourtail.unimined.providers.patch.forge.fg2

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.patch.MinecraftJar
import xyz.wagyourtail.unimined.providers.patch.forge.ForgeMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.util.SemVerUtils
import xyz.wagyourtail.unimined.util.consumerApply
import xyz.wagyourtail.unimined.util.deleteRecursively
import java.net.URI
import java.nio.file.*
import kotlin.io.path.*

class FG2MinecraftTransformer(project: Project, val parent: ForgeMinecraftTransformer) : JarModMinecraftTransformer(
    project,
    parent.provider,
    Constants.FORGE_PROVIDER
) {

    override fun afterEvaluate() {
        // get and add forge-src to mappings
        val forgeDep = parent.forge.dependencies.last()

        val forgeSrc = "${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:src@zip"
        provider.parent.mappingsProvider.getMappings(EnvType.COMBINED).dependencies.apply {
            val empty = isEmpty()
            if (empty) {
                if (!SemVerUtils.matches(provider.minecraftDownloader.version, "<1.7.10")) {
                    add(project.dependencies.create("de.oceanlabs.mcp:mcp:${provider.minecraftDownloader.version}:srg@zip"))
                }
                if (SemVerUtils.matches(provider.minecraftDownloader.version, "<1.7")) {
                    add(project.dependencies.create(forgeSrc))
                } else if (SemVerUtils.matches(provider.minecraftDownloader.version, "<1.7.10")) {
                    throw UnsupportedOperationException("Forge 1.7-1.7.9 don't have automatic mappings support. please supply the mcp mappings or whatever manually")
                } else {
                    if (parent.mcpVersion == null || parent.mcpChannel == null) throw IllegalStateException("mcpVersion and mcpChannel must be set in forge block for 1.7+")
                    add(project.dependencies.create("de.oceanlabs.mcp:mcp_${parent.mcpChannel}:${parent.mcpVersion}@zip"))
                }
            } else {
                val deps = this.toList()
                clear()
                if (!SemVerUtils.matches(provider.minecraftDownloader.version, "<1.7.10")) {
                    add(project.dependencies.create("de.oceanlabs.mcp:mcp:${provider.minecraftDownloader.version}:srg@zip"))
                }
                addAll(deps)
            }
        }

        super.afterEvaluate()
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        val forgeUniversal = parent.forge.dependencies.last()
        val forgeJar = parent.forge.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }

        val outFolder = minecraft.jarPath.parent.resolve("${forgeUniversal.name}-${forgeUniversal.version}")
            .createDirectories()

        // apply binary patches
        val binaryPatchFile = ZipReader.readInputStreamFor("binpatches.pack.lzma", forgeJar.toPath()) {
            outFolder.resolve("binpatches.pack.lzma")
                .apply {
                    writeBytes(
                        it.readBytes(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
                }
        }
        val patchedMC = outFolder.resolve("${minecraft.jarPath.nameWithoutExtension}-${forgeUniversal.name}-${forgeUniversal.version}.${minecraft.jarPath.extension}")
        if (!patchedMC.exists() || project.gradle.startParameter.isRefreshDependencies) {
            patchedMC.deleteIfExists()
            FG2TaskApplyBinPatches(project).doTask(
                minecraft.jarPath.toFile(),
                binaryPatchFile.toFile(),
                patchedMC.toFile(),
                if (minecraft.envType == EnvType.SERVER) "server" else "client"
            )
        }
        //   shade in forge jar
        val shadedForge = super.transform(MinecraftJar(minecraft, patchedMC))
        return MinecraftJar(shadedForge, provider.mcRemapper.provide(shadedForge, "searge", true), shadedForge.envType, "searge")
    }

    override fun applyClientRunConfig(tasks: TaskContainer) {
        provider.provideVanillaRunClientTask(tasks) {
            it.jvmArgs += "-Dfml.ignoreInvalidMinecraftCertificates=true"
            it.jvmArgs += "-Dnet.minecraftforge.gradle.GradleStart.srg.srg-mcp=${parent.srgToMCPAsSRG}"
            it.args += "--tweakClass ${parent.tweakClass ?: "net.minecraftforge.fml.common.launcher.FMLTweaker"}"
        }
    }

    override fun afterRemap(envType: EnvType, namespace: String, baseMinecraft: Path): Path {
        val out = fixForge(envType, namespace, baseMinecraft)
        return ZipReader.openZipFileSystem(out).use { fs ->
            parent.applyATs(
                out,
                listOf(fs.getPath("forge_at.cfg"), fs.getPath("fml_at.cfg")).filter { Files.exists(it) })
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