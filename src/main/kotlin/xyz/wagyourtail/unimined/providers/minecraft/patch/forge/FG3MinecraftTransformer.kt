package xyz.wagyourtail.unimined.providers.minecraft.patch.forge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import xyz.wagyourtail.unimined.providers.minecraft.patch.forge.fg2.FG2TaskApplyBinPatches
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.*
import xyz.wagyourtail.unimined.providers.mappings.MappingExportTypes
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.version.parseAllLibraries
import xyz.wagyourtail.unimined.providers.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.providers.minecraft.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.providers.mod.LazyMutable
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

class FG3MinecraftTransformer(project: Project, val parent: ForgeMinecraftTransformer) : JarModMinecraftTransformer(
    project,
    parent.provider,
    Constants.FORGE_PROVIDER
) {

    val forge = project.configurations.maybeCreate(Constants.FORGE_PROVIDER)
    @ApiStatus.Internal
    val forgeUd = project.configurations.maybeCreate(Constants.FORGE_USERDEV)

    val srgToMcpMappings by lazy { provider.parent.getLocalCache().resolve("mappings").maybeCreate().resolve("srg2mcp.srg").apply {
        provider.parent.mappingsProvider.addExport(EnvType.COMBINED) {
            it.location = toFile()
            it.type = MappingExportTypes.SRG
            it.sourceNamespace = "searge"
            it.targetNamespace = listOf("named")
        }
    }
    }

    private lateinit var tweakClass: String

    override fun afterEvaluate() {
        val forgeDep = forge.dependencies.last()

        // detect if userdev3 or userdev
        //   read if forgeDep has binpatches file
        val forgeUni = forge.getFile(forgeDep)
        val userdevClassifier = ZipReader.readInputStreamFor<String?>("binpatches.pack.lzma", forgeUni.toPath(), false) {
            "userdev3"
        } ?: "userdev"

        val userdev = "${forgeDep.group}:${forgeDep.name}:${forgeDep.version}:$userdevClassifier"
        forgeUd.dependencies.add(project.dependencies.create(userdev))

        super.afterEvaluate()
    }

    fun Configuration.getFile(dep: Dependency, extension: String = "jar"): File {
        resolve()
        return files(dep).first { it.extension == extension }
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        TODO()
    }

    override fun applyClientRunConfig(tasks: TaskContainer) {
        provider.provideRunClientTask(tasks) {
            TODO()
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