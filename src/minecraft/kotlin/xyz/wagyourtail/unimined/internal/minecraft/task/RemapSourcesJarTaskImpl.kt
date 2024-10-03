package xyz.wagyourtail.unimined.internal.minecraft.task

import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.util.deleteRecursively
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.inject.Inject
import kotlin.io.path.*

abstract class RemapSourcesJarTaskImpl @Inject constructor(provider: MinecraftConfig): RemapJarTaskImpl(provider) {
    @OptIn(ExperimentalPathApi::class)
    override fun remapToInternal(
        from: Path,
        target: Path,
        fromNs: MappingNamespaceTree.Namespace,
        toNs: MappingNamespaceTree.Namespace,
        classpathList: Array<Path>
    ) {
        // source-remap seems to be broken when reading/writing from a jar, so copy them to/from temp dirs
        val output = temporaryDir.resolve(toNs.name).toPath().apply {
            if(this.exists()) deleteRecursively()
            createDirectories()
        }

        val input = temporaryDir.resolve(fromNs.name).toPath().apply {
            if(this.exists()) deleteRecursively()
            createDirectories()

            project.copy {
                it.from(project.zipTree(from))
                it.into(this)
            }
        }

        provider.sourceProvider.sourceRemapper.remap(
            mapOf(input to output),
            project.files(*classpathList),
            fromNs,
            fromNs,
            toNs,
            toNs,
            specConfig = {
                standardOutput = temporaryDir.resolve("remap-${fromNs}-to-${toNs}.log").outputStream()
            }
        )

        // copy non-source files directly
        input.walk().filter { it.extension != "java" && it.extension != "kt" }.forEach { file ->
            val name = input.relativize(file).toString().replace('\\', '/')
            val targetFile = output.resolve(name)
            targetFile.parent.createDirectories()
            file.copyTo(targetFile, overwrite = true)
        }

        JarOutputStream(target.toFile().outputStream()).use { zos ->
            output.walk().forEach { file ->
                val name = output.relativize(file).toString().replace('\\', '/')
                zos.putNextEntry(JarEntry(name))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}