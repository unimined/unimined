package xyz.wagyourtail.unimined.providers.patch.jarmod

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.configurationcache.extensions.capitalized
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
import java.io.IOException
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipInputStream
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

open class JarModMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider,
    private val jarModProvider: String = Constants.JARMOD_PROVIDER,
) : AbstractMinecraftTransformer(
    project, provider
) {
    private fun jarModConfiguration(envType: EnvType): Configuration? {
        return project.configurations.findByName(jarModProvider + (envType.classifier?.capitalized() ?: ""))
    }

    var clientMainClass: String? = null
    var serverMainClass: String? = null


    private val combinedNamesMap = mutableMapOf<EnvType, String>()
    private fun getCombinedNames(envType: EnvType): String {
        return combinedNamesMap.computeIfAbsent(envType) {
            val thisEnv = jarModConfiguration(envType)?.dependencies?.toMutableSet() ?: mutableSetOf()
            if (envType != EnvType.COMBINED) {
                thisEnv.addAll(jarModConfiguration(EnvType.COMBINED)?.dependencies ?: setOf())
            }
            val jarMod = thisEnv.sortedBy { "${it.name}-${it.version}" }
            jarMod.joinToString("+") { it.name + "-" + it.version }
        }
    }

    override fun transform(envType: EnvType, baseMinecraft: Path): Path {
        val combinedNames = getCombinedNames(envType)
        val target = getTransformedMinecraftPath(baseMinecraft, combinedNames)
        if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            return target
        }

        val jarmod = jarModConfiguration(envType)?.resolve()?.toMutableSet() ?: mutableSetOf()
        if (envType != EnvType.COMBINED) {
            jarmod.addAll(jarModConfiguration(EnvType.COMBINED)?.resolve() ?: setOf())
        }

        Files.copy(baseMinecraft, target, StandardCopyOption.REPLACE_EXISTING)
        val mc = URI.create("jar:${target.toUri()}")
        try {
            FileSystems.newFileSystem(mc, mapOf("mutable" to true), null).use { out ->
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
                    if (out.getPath("META-INF").exists()) {
                        Files.walkFileTree(out.getPath("META-INF"), object : SimpleFileVisitor<Path>() {
                            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                                dir.deleteExisting()
                                return FileVisitResult.CONTINUE
                            }

                            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                                file.deleteExisting()
                                return FileVisitResult.CONTINUE
                            }
                        })
                    }
                }
            }
        } catch (e: Throwable) {
            target.deleteExisting()
            throw e
        }
        return target
    }

    private fun getTransformedMinecraftPath(baseMinecraft: Path, combinedNames: String): Path {
        return baseMinecraft.parent.resolve("${baseMinecraft.nameWithoutExtension}-${combinedNames}.jar")
    }

}