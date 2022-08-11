package xyz.wagyourtail.unimined.providers.patch.jarmod

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.Constants
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
    jarModProvider: String = Constants.JARMOD_PROVIDER,
    jarModServerProvider: String = Constants.JARMODSERVER_PROVIDER
) : AbstractMinecraftTransformer(
    project,
    provider
) {
    val jarMod: Configuration by lazy { project.configurations.getByName(jarModProvider) }
    val jarModServer: Configuration by lazy { project.configurations.getByName(jarModServerProvider) }

    var clientMainClass: String? = null
    var serverMainClass: String? = null

    val combinedNames: String by lazy {
        val jarMod = (jarMod.dependencies as Set<Dependency>).sortedBy { "${it.name}-${it.version}" }
        jarMod.joinToString("+") { it.name + "-" + it.version }
    }

    val combinedNamesServer: String by lazy {
        val jarMod = (jarModServer.dependencies as Set<Dependency>).sortedBy { "${it.name}-${it.version}" }
        jarMod.joinToString("+") { it.name + "-" + it.version }
    }

    override fun transformClient(baseMinecraft: Path): Path = transformCombined(baseMinecraft, jarMod, combinedNames)

    override fun transformServer(baseMinecraft: Path): Path =
        transformCombined(baseMinecraft, jarModServer, combinedNamesServer)


    override fun transformCombined(baseMinecraft: Path): Path {
        return transformClient(baseMinecraft)
    }

    fun transformCombined(baseMinecraft: Path, jarmod: Configuration, combinedNames: String): Path {
        val target = getTransformedMinecraftPath(baseMinecraft, combinedNames)
        if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
            return target
        }
        Files.copy(baseMinecraft, target, StandardCopyOption.REPLACE_EXISTING)
        val mc = URI.create("jar:${target.toUri()}")
        try {
            FileSystems.newFileSystem(mc, mapOf("mutable" to true), null).use { out ->
                for (file in jarmod.resolve()) {
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