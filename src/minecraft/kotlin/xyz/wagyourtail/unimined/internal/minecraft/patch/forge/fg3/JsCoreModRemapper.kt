package xyz.wagyourtail.unimined.internal.minecraft.patch.forge.fg3

import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.logging.Logger
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.writer

class JsCoreModRemapper(val logger: Logger): OutputConsumerPath.ResourceRemapper {

    override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
        return relativePath.parent?.name == "coremods" &&
                relativePath.extension in setOf("js", "json")
    }

    val classDescRegex = Regex("L([^;]+);")
    val strRegex = Regex("(?<!\\\\)([\"'])(.+?)(?<!\\\\)\\1")

    override fun transform(
        destinationDirectory: Path,
        relativePath: Path,
        input: InputStream,
        remapper: TinyRemapper
    ) {
        val output = destinationDirectory.resolve(relativePath)
        output.parent.createDirectories()
        input.reader().buffered().use { reader ->
            output.writer(StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).buffered().use { os ->
                reader.lines().map { line ->
                    line.replace(classDescRegex) {
                        val className = it.groupValues[1]
                        val remapped = remapper.environment.remapper.map(className)
                        if (remapped != className) {
                            "L$remapped;"
                        } else {
                            it.value
                        }
                    }.replace(strRegex) { match ->
                        val str = match.groupValues[2]
                        if (str.contains('.') && str.contains('/')) {
                            // not a class name, would break in remapping
                            match.value
                        } else {
                            val dotty = str.contains('.')
                            val remapped = remapper.environment.remapper.map(str.replace('.', '/'))
                                .let { if (dotty) it.replace('/', '.') else it }
                            if (remapped != str) {
                                "${match.groupValues[1]}$remapped${match.groupValues[1]}"
                            } else {
                                match.value
                            }
                        }
                    }
                }.forEach {
                    os.write("$it\n")
                }
            }
        }
    }
}
