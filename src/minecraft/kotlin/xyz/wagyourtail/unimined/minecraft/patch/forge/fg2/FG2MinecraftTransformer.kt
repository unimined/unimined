package xyz.wagyourtail.unimined.minecraft.patch.forge.fg2

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.launch.LaunchConfig
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.minecraft.patch.forge.ForgeMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.transform.merge.ClassMerger
import xyz.wagyourtail.unimined.util.SemVerUtils
import xyz.wagyourtail.unimined.util.deleteRecursively
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class FG2MinecraftTransformer(project: Project, val parent: ForgeMinecraftTransformer) : JarModMinecraftTransformer(
    project,
    parent.provider,
    Constants.FORGE_PROVIDER
) {

    override val prodNamespace = MappingNamespace.SEARGE

    override var devNamespace: MappingNamespace
        get() = parent.devNamespace
        set(value) {
            parent.devNamespace = value
        }

    override var devFallbackNamespace: MappingNamespace
        get() = parent.devFallbackNamespace
        set(value) {
            parent.devFallbackNamespace = value
        }

    override val merger: ClassMerger
        get() = parent.merger

    override fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar): MinecraftJar {
        val clientPatched = transformIntern(clientjar)
        val serverPatched = transformIntern(serverjar)
        return super.merge(clientPatched, serverPatched)
    }

    override fun afterEvaluate() {
        // get and add forge-src to mappings
        val forgeDep = parent.forge.dependencies.last()

        val forgeSrc = "${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:src@zip"
        project.mappings.getMappings(EnvType.COMBINED).dependencies.apply {
            val empty = isEmpty()
            if (empty) {
                if (!SemVerUtils.matches(provider.minecraft.version, "<1.7.10")) {
                    add(project.dependencies.create("de.oceanlabs.mcp:mcp:${provider.minecraft.version}:srg@zip"))
                }
                if (SemVerUtils.matches(provider.minecraft.version, "<1.7")) {
                    add(project.dependencies.create(forgeSrc))
                } else if (SemVerUtils.matches(provider.minecraft.version, "<1.7.10")) {
                    throw UnsupportedOperationException("Forge 1.7-1.7.9 don't have automatic mappings support. please supply the mcp mappings or whatever manually")
                } else {
                    if (parent.mcpVersion == null || parent.mcpChannel == null) throw IllegalStateException("mcpVersion and mcpChannel must be set in forge block for 1.7+")
                    add(project.dependencies.create("de.oceanlabs.mcp:mcp_${parent.mcpChannel}:${parent.mcpVersion}@zip"))
                }
            } else {
                val deps = this.toList()
                clear()
                if (!SemVerUtils.matches(provider.minecraft.version, "<1.7.10")) {
                    add(project.dependencies.create("de.oceanlabs.mcp:mcp:${provider.minecraft.version}:srg@zip"))
                }
                addAll(deps)
            }
        }
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        val shadedForge = super.transform(if (minecraft.envType == EnvType.COMBINED) minecraft else transformIntern(minecraft))
        return provider.mcRemapper.provide(shadedForge, MappingNamespace.SEARGE, MappingNamespace.OFFICIAL)
    }

    private fun transformIntern(minecraft: MinecraftJar): MinecraftJar {
        val forgeUniversal = parent.forge.dependencies.last()
        val forgeJar = parent.forge.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }

        val outFolder = minecraft.path.parent.resolve("${forgeUniversal.name}-${forgeUniversal.version}")
            .createDirectories()

        val patchedMC = MinecraftJar(
            minecraft,
            name = "forge",
            parentPath = outFolder,
        )

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

        if (!patchedMC.path.exists() || project.gradle.startParameter.isRefreshDependencies) {
            patchedMC.path.deleteIfExists()
            FG2TaskApplyBinPatches(project).doTask(
                minecraft.path.toFile(),
                binaryPatchFile.toFile(),
                patchedMC.path.toFile(),
                if (minecraft.envType == EnvType.SERVER) "server" else "client"
            )
        }
        return patchedMC
    }

    override fun applyClientRunTransform(config: LaunchConfig) {
        config.mainClass = parent.mainClass ?: config.mainClass
        config.jvmArgs += "-Dfml.ignoreInvalidMinecraftCertificates=true"
        config.jvmArgs += "-Dfml.deobfuscatedEnvironment=true"
        config.jvmArgs += "-Dnet.minecraftforge.gradle.GradleStart.srg.srg-mcp=${parent.srgToMCPAsSRG}"
        config.args += "--tweakClass ${parent.tweakClassClient ?: "net.minecraftforge.fml.common.launcher.FMLTweaker"}"
    }

    override fun applyServerRunTransform(config: LaunchConfig) {
        config.mainClass = parent.mainClass ?: config.mainClass
        config.jvmArgs += "-Dfml.ignoreInvalidMinecraftCertificates=true"
        config.jvmArgs += "-Dfml.deobfuscatedEnvironment=true"
        config.jvmArgs += "-Dnet.minecraftforge.gradle.GradleStart.srg.srg-mcp=${parent.srgToMCPAsSRG}"
        config.args += "--tweakClass ${parent.tweakClassServer ?: "net.minecraftforge.fml.common.launcher.FMLServerTweaker"}"
    }

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar {
        val out = fixForge(baseMinecraft)
        return ZipReader.openZipFileSystem(out.path).use { fs ->
            parent.applyATs(
                out,
                listOf(fs.getPath("forge_at.cfg"), fs.getPath("fml_at.cfg")).filter { Files.exists(it) })
        }
    }

    private fun fixForge(baseMinecraft: MinecraftJar): MinecraftJar {
        if (baseMinecraft.mappingNamespace.type == MappingNamespace.Type.NAMED) {
            val target = MinecraftJar(
                baseMinecraft,
                patches = baseMinecraft.patches + "fixForge",
            )

            if (target.path.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                return target
            }

            Files.copy(baseMinecraft.path, target.path, StandardCopyOption.REPLACE_EXISTING)
            val mc = URI.create("jar:${target.path.toUri()}")
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
                }
            } catch (e: Throwable) {
                target.path.deleteIfExists()
                throw e
            }
            return target
        }
        return baseMinecraft
    }


}