package xyz.wagyourtail.unimined.providers.minecraft.patch.fabric

import net.fabricmc.accesswidener.AccessWidenerReader
import net.fabricmc.accesswidener.AccessWidenerRemapper
import net.fabricmc.accesswidener.AccessWidenerWriter
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.configurationcache.extensions.capitalized
import org.slf4j.LoggerFactory
import xyz.wagyourtail.unimined.maybeCreate
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.MinecraftJar
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.extension
import kotlin.io.path.readText

object AccessWidenerMinecraftTransformer {

    fun awRemapper(source: String, target: String): OutputConsumerPath.ResourceRemapper = object : OutputConsumerPath.ResourceRemapper {
        override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
            // read the beginning of the file and see if it begins with "accessWidener"
            return relativePath.extension.equals("accesswidener", true) ||
                    relativePath.extension.equals("aw", true)
        }

        override fun transform(
            destinationDirectory: Path,
            relativePath: Path,
            input: InputStream,
            remapper: TinyRemapper
        ) {
            val awr = AccessWidenerWriter()
            AccessWidenerReader(AccessWidenerRemapper(awr, remapper.environment.remapper, source, target)).read(BufferedReader(InputStreamReader(input)))
            val output = destinationDirectory.resolve(relativePath)
            output.parent.maybeCreate()
            Files.write(output, awr.write(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
    }

}