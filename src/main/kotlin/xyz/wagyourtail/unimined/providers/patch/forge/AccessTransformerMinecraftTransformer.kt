package xyz.wagyourtail.unimined.providers.patch.forge

import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.minecraftforge.accesstransformer.TransformerProcessor
import org.objectweb.asm.commons.Remapper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.name

object AccessTransformerMinecraftTransformer {

    fun atRemapper(remapToLegacy: Boolean = false): OutputConsumerPath.ResourceRemapper =
        object : OutputConsumerPath.ResourceRemapper {
            override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
                return relativePath.name == "accesstransformer.cfg" ||
                        relativePath.name == "fml_at.cfg" ||
                        relativePath.name == "forge_at.cfg"
            }

            override fun transform(
                destinationDirectory: Path,
                relativePath: Path,
                input: InputStream,
                remapper: TinyRemapper
            ) {
                val output = destinationDirectory.resolve(relativePath)
                BufferedReader(input.reader()).use { reader ->
                    Files.newBufferedWriter(
                        output,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    ).use { writer ->
                        var line = reader.readLine()
                        while (line != null) {
                            line = transformLegacyTransformer(line)
                            line = remapModernTransformer(line, remapper.environment.remapper)
                            if (remapToLegacy) {
                                TODO()
                            }
                            writer.write("$line\n")
                            line = reader.readLine()
                        }
                    }
                }
            }

        }

    private val legacyMethod = Regex(
        "^(\\w+(?:[\\-+]f)?)\\s+([\\w.$]+)\\.([\\w*<>]+)(\\(.+?)\\s*?(#.+?)?\$",
        RegexOption.MULTILINE
    )
    private val legacyField = Regex(
        "^(\\w+(?:[\\-+]f)?)\\s+([\\w.$]+)\\.([\\w*<>]+)\\s*?(#.+?)?\$",
        RegexOption.MULTILINE
    )

    private val modernClass = Regex("^(\\w+(?:[\\-+]f)?)\\s+([\\w.$]+)\\s*?(#.+?)?\$", RegexOption.MULTILINE)
    private val modernMethod = Regex(
        "^(\\w+(?:[\\-+]f)?)\\s+([\\w.\$]+)\\s+([\\w*<>]+)(\\(.+?)\\s*?(#.+?)?\$",
        RegexOption.MULTILINE
    )
    private val modernField = Regex(
        "^(\\w+(?:[\\-+]f)?)\\s+([\\w.\$]+)\\s+([\\w*<>]+)\\s*?(#.+?)?\$",
        RegexOption.MULTILINE
    )

    private fun transformFromLegacyTransformer(reader: BufferedReader, writer: BufferedWriter) {
        var line = reader.readLine()
        while (line != null) {
            writer.write("${transformLegacyTransformer(line)}\n")
            line = reader.readLine()
        }
    }

    private fun transformLegacyTransformer(line: String): String {
        val methodMatch = legacyMethod.matchEntire(line)
        if (methodMatch != null) {
            val (access, owner, name, desc, comment) = methodMatch.destructured
            return if (desc.endsWith(")")) {
                if (!name.contains("<")) {
                    throw IllegalStateException("Missing return type in access transformer: $line")
                }
                "$access $owner $name${desc}V $comment"
            } else {
                "$access $owner $name${desc} $comment"
            }
        }
        val fieldMatch = legacyField.matchEntire(line)
        if (fieldMatch != null) {
            val (access, owner, name, comment) = fieldMatch.destructured
            return "$access $owner $name $comment"
        }
        return line
    }

    private fun remapModernTransformer(reader: BufferedReader, writer: BufferedWriter, remapper: Remapper) {
        var line = reader.readLine()
        while (line != null) {
            writer.write("${remapModernTransformer(line, remapper)}\n")
            line = reader.readLine()
        }
    }

    private fun remapModernTransformer(line: String, remapper: Remapper): String {
        val classMatch = modernClass.matchEntire(line)
        if (classMatch != null) {
            val (access, owner, comment) = classMatch.destructured
            val remappedOwner = remapper.map(owner.replace(".", "/")).replace("/", ".")
            return "$access $remappedOwner $comment"
        }
        val methodMatch = modernMethod.matchEntire(line)
        if (methodMatch != null) {
            val (access, owner, name, desc, comment) = methodMatch.destructured
            val remappedOwner = remapper.map(owner.replace(".", "/")).replace("/", ".")
            if (name == "*") {
                if (desc == "()") {
                    return "$access $remappedOwner $name$desc $comment"
                }
            }
            val remappedName = remapper.mapMethodName(remappedOwner, name, desc)
            val remappedDesc = remapper.mapMethodDesc(desc)
            return "$access $remappedOwner $remappedName$remappedDesc $comment"
        }
        val fieldMatch = modernField.matchEntire(line)
        if (fieldMatch != null) {
            val (access, owner, name, comment) = fieldMatch.destructured
            val remappedOwner = remapper.map(owner.replace(".", "/")).replace("/", ".")
            val remappedName = remapper.mapFieldName(remappedOwner, name, null)
            return "$access $remappedOwner $remappedName $comment"
        }
        println("Failed to match: $line")
        return line
    }

    fun transform(accessTransformers: List<Path>, baseMinecraft: Path, output: Path): Path {
        if (accessTransformers.isEmpty()) {
            return baseMinecraft
        }
        val transfomerProcessor = TransformerProcessor::class.java
        val processJar = transfomerProcessor.getDeclaredMethod(
            "processJar",
            Path::class.java,
            Path::class.java,
            List::class.java
        )
        processJar.isAccessible = true
        processJar(null, baseMinecraft, output, accessTransformers)
        return output
    }
}