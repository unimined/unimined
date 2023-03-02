package xyz.wagyourtail.unimined.minecraft.patch.jarmod

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.configurationcache.extensions.capitalized
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.launch.LaunchConfig
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.JarModPatcher
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.minecraft.transform.fixes.ModLoaderPatches
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.consumerApply
import xyz.wagyourtail.unimined.util.deleteRecursively
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

open class JarModMinecraftTransformer(
    project: Project,
    provider: MinecraftProviderImpl,
    private val jarModProvider: String = Constants.JARMOD_PROVIDER,
) : AbstractMinecraftTransformer(
    project, provider, "jarmod"
), JarModPatcher {

    override var devNamespace by LazyMutable { MappingNamespace.findByType(MappingNamespace.Type.NAMED, project.mappings.getAvailableMappings(project.minecraft.defaultEnv)) }
    override var devFallbackNamespace by LazyMutable { MappingNamespace.findByType(MappingNamespace.Type.INT, project.mappings.getAvailableMappings(project.minecraft.defaultEnv)) }
    override var deleteMetaInf: Boolean = false

    init {
        for (envType in EnvType.values()) {
            jarModConfiguration(envType)
        }
    }

    override val transform = (listOf<(FileSystem) -> Unit>(
        ModLoaderPatches::fixURIisNotHierarchicalException,
        ModLoaderPatches::fixLoadingModFromOtherPackages
    ) + super.transform).toMutableList()

    fun addTransform(pathFilter: (FileSystem) -> Unit) {
        transform.add(pathFilter)
    }

    fun jarModConfiguration(envType: EnvType): Configuration {
        return project.configurations.maybeCreate(jarModProvider + (envType.classifier?.capitalized() ?: ""))
    }

    var clientMainClass: String? = null
    var serverMainClass: String? = null


    private val combinedNamesMap = mutableMapOf<EnvType, String>()
    private fun getCombinedNames(envType: EnvType): String {
        return combinedNamesMap.computeIfAbsent(envType) {
            val thisEnv = jarModConfiguration(envType).dependencies.toMutableSet()
            if (envType != EnvType.COMBINED) {
                thisEnv.addAll(jarModConfiguration(EnvType.COMBINED).dependencies)
            }
            val jarMod = thisEnv.sortedBy { "${it.name}-${it.version}" }
            jarMod.joinToString("+") { it.name + "-" + it.version }
        }
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        val combinedNames = getCombinedNames(minecraft.envType)
        if (combinedNames.isEmpty()) {
            return minecraft
        }
        return minecraft.let(consumerApply {
            val target = MinecraftJar(
                minecraft,
                patches = minecraft.patches + combinedNames
            )
            if (target.path.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                return@consumerApply target
            }

            val jarmod = jarModConfiguration(envType).resolve().toMutableSet()
            if (envType != EnvType.COMBINED) {
                jarmod.addAll(jarModConfiguration(EnvType.COMBINED).resolve())
            }

            try {
                Files.copy(path, target.path, StandardCopyOption.REPLACE_EXISTING)
                ZipReader.openZipFileSystem(target.path, mapOf("mutable" to true)).use { out ->
                    if (out.getPath("META-INF").exists() && deleteMetaInf) {
                        out.getPath("META-INF").deleteRecursively()
                    }
                    for (file in jarmod) {
                        ZipInputStream(file.inputStream()).use {
                            var entry = it.nextEntry
                            while (entry != null) {
                                if (entry.isDirectory) {
                                    Files.createDirectories(out.getPath(entry.name))
                                } else {
                                    out.getPath(entry.name).parent?.let { path ->
                                        Files.createDirectories(path)
                                    }
                                    Files.write(
                                        out.getPath(entry.name),
                                        it.readBytes(),
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.TRUNCATE_EXISTING
                                    )
                                }
                                entry = it.nextEntry
                            }
                        }
                    }
                    transform.forEach { it(out) }
                }
            } catch (e: Throwable) {
                target.path.deleteIfExists()
                throw e
            }
            target
        })
    }

    override fun applyClientRunTransform(config: LaunchConfig) {
        config.mainClass = clientMainClass ?: config.mainClass
    }

    override fun applyServerRunTransform(config: LaunchConfig) {
        config.mainClass = serverMainClass ?: config.mainClass
    }
}