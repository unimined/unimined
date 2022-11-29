package xyz.wagyourtail.unimined.refmap

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import net.fabricmc.tinyremapper.extension.mixin.common.Logger
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData
import org.gradle.api.logging.LogLevel
import org.objectweb.asm.ClassVisitor
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.writeText

class RefmapBuilder(val defaultRefmapPath: String, val loggerLevel: LogLevel = LogLevel.WARN) :
    MixinExtension(setOf(AnnotationTarget.HARD), translateLogLevel(loggerLevel)),
        TinyRemapper.Extension,
        TinyRemapper.ApplyVisitorProvider,
        OutputConsumerPath.ResourceRemapper {

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        private fun translateLogLevel(loggerLevel: LogLevel) = when (loggerLevel) {
                LogLevel.DEBUG -> Logger.Level.INFO
                LogLevel.INFO -> Logger.Level.INFO
                LogLevel.WARN -> Logger.Level.WARN
                LogLevel.ERROR -> Logger.Level.ERROR
                LogLevel.QUIET -> Logger.Level.ERROR
                else -> Logger.Level.WARN
            }
    }

    val defaultRefmap = JsonObject()
    val refmaps = mutableMapOf<String, JsonObject>(defaultRefmapPath to defaultRefmap)
    val classesToRefmap = mutableMapOf<String, MutableSet<String>>()

    private val logger: Logger = Logger(translateLogLevel(loggerLevel))

    override fun attach(builder: TinyRemapper.Builder) {
        super.attach(builder)
        logger.info("Attaching RefmapBuilder")
        builder.extraPreApplyVisitor(this)
    }

    override fun insertApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor {
        // detect if class in class lists
        if (classesToRefmap.containsKey(cls.name.replace("/", "."))) {
            logger.info("Found mixin class: ${cls.name}")
            val refmaps = classesToRefmap[cls.name.replace("/", ".")]!!.map { refmaps[it]!! }
            val target = JsonObject()
            for (refmap in refmaps) {
                if (!refmap.has("mappings")) {
                    refmap.add("mappings", JsonObject())
                }
                val mappings = refmap.getAsJsonObject("mappings")
                mappings.add(cls.name, target)
            }
            return MixinClassVisitorRefmapBuilder(CommonData(cls.environment, logger), cls.name, target, next)
        }
        return next
    }

    fun write(fs: FileSystem) {
        for ((path, refmap) in refmaps) {
            fs.getPath(path).writeText(GSON.toJson(refmap), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
    }

    override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
        return relativePath.name.endsWith("mixins.json")
    }

    override fun transform(
        destinationDirectory: Path,
        relativePath: Path,
        input: InputStream,
        remapper: TinyRemapper
    ) {
        val json = JsonParser.parseReader(input.reader()).asJsonObject
        val refmap = json.get("refmap")?.asString ?: defaultRefmapPath
        val pkg = json.get("package").asString
        refmaps.computeIfAbsent(refmap) { JsonObject() }
        for (mixin in json.getAsJsonArray("mixin") ?: listOf()) {
            classesToRefmap.computeIfAbsent("$pkg.${mixin.asString}") { mutableSetOf() } += refmap
        }
        for (mixin in json.getAsJsonArray("client") ?: listOf()) {
            classesToRefmap.computeIfAbsent("$pkg.${mixin.asString}") { mutableSetOf() } += refmap
        }
        for (mixin in json.getAsJsonArray("server") ?: listOf()) {
            classesToRefmap.computeIfAbsent("$pkg.${mixin.asString}") { mutableSetOf() } += refmap
        }
        json.addProperty("refmap", refmap)
        val output = destinationDirectory.resolve(relativePath)
        output.parent.createDirectories()
        output.writeText(GSON.toJson(json), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
}