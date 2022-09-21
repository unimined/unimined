package xyz.wagyourtail.unimined.providers.minecraft.patch.forge.fg2

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.*
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.providers.minecraft.patch.forge.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.forge.ForgeMinecraftTransformer
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

    fun Configuration.getFile(dep: Dependency, extension: String = "jar"): File {
        resolve()
        return files(dep).first { it.extension == extension }
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        val atOut = minecraft.let(consumerApply {
            val forgeUniversal = parent.forge.dependencies.last()
            val forgeJar = parent.forge.files(forgeUniversal).first { it.extension == "zip" || it.extension == "jar" }

            val outFolder = jarPath.parent.resolve("${forgeUniversal.name}-${forgeUniversal.version}").maybeCreate()

            // apply binary patches
            val binaryPatchFile = ZipReader.readInputStreamFor("binpatches.pack.lzma", forgeJar.toPath()) {
                outFolder.resolve("binpatches.pack.lzma").apply { writeBytes(it.readBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING) }
            }
            val patchedMC = outFolder.resolve("${jarPath.nameWithoutExtension}-${forgeUniversal.name}-${forgeUniversal.version}.${jarPath.extension}")
            if (!patchedMC.exists() || project.gradle.startParameter.isRefreshDependencies) {
                patchedMC.deleteIfExists()
                FG2TaskApplyBinPatches(project).doTask(jarPath.toFile(), binaryPatchFile.toFile(), patchedMC.toFile(), if (envType == EnvType.SERVER) "server" else "client")
            }

            val accessModder = AccessTransformerMinecraftTransformer(project, provider, envType).apply {
                atTransformers.add(::transformLegacyTransformer)
                atTransformers.add {
                    remapTransformer(
                        envType,
                        it,
                        "named", "searge", "official", "official"
                    )
                }

                ZipReader.readInputStreamFor("fml_at.cfg", forgeJar.toPath(), false) {
                    addAccessTransformer(it)
                }

                ZipReader.readInputStreamFor("forge_at.cfg", forgeJar.toPath(), false) {
                    addAccessTransformer(it)
                }

                parent.accessTransformer?.let { addAccessTransformer(it) }
            }

            accessModder.transform(MinecraftJar(this, patchedMC))
        })

        return super.transform(atOut)
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