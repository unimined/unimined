package xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.configurationcache.extensions.capitalized
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.JarModPatcher
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.internal.minecraft.transform.fixes.ModLoaderPatches
import xyz.wagyourtail.unimined.util.consumerApply
import xyz.wagyourtail.unimined.util.deleteRecursively
import xyz.wagyourtail.unimined.util.withSourceSet
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

open class JarModMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider,
    private val jarModProvider: String = "jarMod",
    providerName: String = "JarMod"
): AbstractMinecraftTransformer(
    project, provider, providerName
), JarModPatcher {

    override var deleteMetaInf: Boolean = false

    val jarModConfiguration = project.configurations.maybeCreate(jarModProvider.withSourceSet(provider.sourceSet))

    override val transform = (listOf<(FileSystem) -> Unit>(
        ModLoaderPatches::fixURIisNotHierarchicalException,
        ModLoaderPatches::fixLoadingModFromOtherPackages
    ) + super.transform).toMutableList()

    fun addTransform(pathFilter: (FileSystem) -> Unit) {
        transform.add(pathFilter)
    }

    private val combinedNames by lazy {
        val jarMod = jarModConfiguration.dependencies.sortedBy { "${it.name}-${it.version}" }
        jarMod.joinToString("+") { it.name + "-" + it.version }
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
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

            val jarmod = jarModConfiguration.resolve().toMutableSet()

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

    protected fun detectProjectSourceSets(): Set<SourceSet> {
        val sourceSets = mutableSetOf<SourceSet>()
        val projects = project.rootProject.allprojects
        for (project in projects) {
            for (sourceSet in project.extensions.findByType(SourceSetContainer::class.java)?.asMap?.values
                ?: listOf()) {
                if (sourceSet.output.files.intersect(provider.sourceSet.runtimeClasspath.files).isNotEmpty()) {
                    sourceSets.add(sourceSet)
                }
            }
        }
        return sourceSets
    }
}