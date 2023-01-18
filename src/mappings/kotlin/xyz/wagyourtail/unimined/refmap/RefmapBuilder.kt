package xyz.wagyourtail.unimined.refmap

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrEnvironment
import net.fabricmc.tinyremapper.extension.mixin.common.Logger
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData
import org.gradle.api.logging.LogLevel
import org.objectweb.asm.ClassVisitor
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.writeText

class RefmapBuilder(val defaultRefmapPath: String, val loggerLevel: LogLevel = LogLevel.WARN) :
        TinyRemapper.Extension,
        TinyRemapper.ApplyVisitorProvider,
        TinyRemapper.AnalyzeVisitorProvider,
        TinyRemapper.StateProcessor,
        OutputConsumerPath.ResourceRemapper {
    private val tasks: MutableMap<Int, MutableList<Consumer<CommonData>>> = mutableMapOf()

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
    val refmaps = mutableMapOf(defaultRefmapPath to defaultRefmap)
    val classesToRefmap = mutableMapOf<String, MutableSet<String>>()

    val existingRefmaps = mutableMapOf<String, JsonObject>()

    private val logger: Logger = Logger(translateLogLevel(loggerLevel))

    override fun attach(builder: TinyRemapper.Builder) {
        builder.extraAnalyzeVisitor(this).extraStateProcessor(this)
        logger.info("Attaching RefmapBuilder")
        builder.extraPreApplyVisitor(this)
    }

    override fun insertApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor {
        // detect if class in class lists
        if (classesToRefmap.containsKey(cls.name.replace("/", "."))) {
            logger.info("Found mixin class: ${cls.name}")
            val refmapNames = classesToRefmap[cls.name.replace("/", ".")]
            val target = JsonObject()
            val existingRefmaps = refmapNames!!.mapNotNull { existingRefmaps[it] }
            logger.info("Found ${existingRefmaps.size} existing refmaps")
            val existingClassMappings = if (existingRefmaps.isNotEmpty()) {
                existingRefmaps.mapNotNull { it.get("mappings").asJsonObject.get(cls.name)?.asJsonObject }
            } else {
                setOf()
            }
            // combine all mappings
            val combinedMappings = mutableMapOf<String, String>()
            for (mapping in existingClassMappings) {
                for ((key, value) in mapping.entrySet()) {
                    combinedMappings[key] = value.asString
                }
            }
            return MixinClassVisitorRefmapBuilder(CommonData(cls.environment, logger), cls.name, target, next, combinedMappings) {
                if (target.size() > 0) {
                    val refmaps = refmapNames.map { refmaps[it]!! }
                    for (refmap in refmaps) {
                        if (!refmap.has("mappings")) {
                            refmap.add("mappings", JsonObject())
                        }
                        val mappings = refmap.getAsJsonObject("mappings")
                        mappings.add(cls.name, target)
                    }
                }
            }
        }
        return next
    }


    override fun insertAnalyzeVisitor(mrjVersion: Int, className: String, next: ClassVisitor?): ClassVisitor? {
        if (classesToRefmap.containsKey(className.replace("/", "."))) {
            tasks.putIfAbsent(mrjVersion, mutableListOf())

            val refmapNames = classesToRefmap[className.replace("/", ".")]
            val existingRefmaps = refmapNames!!.mapNotNull { existingRefmaps[it] }
            logger.info("Found ${existingRefmaps.size} existing refmaps")
            val existingClassMappings = if (existingRefmaps.isNotEmpty()) {
                existingRefmaps.mapNotNull { it.get("mappings").asJsonObject.get(className)?.asJsonObject }
            } else {
                setOf()
            }
            // combine all mappings
            val combinedMappings = mutableMapOf<String, String>()
            for (mapping in existingClassMappings) {
                for ((key, value) in mapping.entrySet()) {
                    combinedMappings[key] = value.asString
                }
            }
            return HarderTargetMixinClassVisitor(tasks[mrjVersion]!!, next, combinedMappings)
        }
        return next
    }

    override fun process(environment: TrEnvironment) {
        val data = CommonData(environment, logger)

        for (task in tasks[environment.mrjVersion] ?: listOf()) {
            try {
                task.accept(data)
            } catch (e: RuntimeException) {
                logger.error(e.message)
            }
        }
    }

    fun write(fs: FileSystem) {
        for ((path, refmap) in refmaps) {
            if (refmap.size() > 0)
                fs.getPath(path)
                    .writeText(
                        GSON.toJson(refmap),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
        }
    }

    fun mixinCheck(relativePath: Path): Boolean =
        relativePath.name.contains("mixins") && relativePath.name.endsWith(".json")

    fun refmapCheck(relativePath: Path): Boolean =
        relativePath.name.contains("refmap") && relativePath.name.endsWith(".json")

    override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
        return mixinCheck(relativePath) || refmapCheck(relativePath)
    }

    override fun transform(
        destinationDirectory: Path,
        relativePath: Path,
        input: InputStream,
        remapper: TinyRemapper
    ) {
        if (mixinCheck(relativePath)) {
            try {
                logger.info("Found mixin config: ${relativePath.name}")
                val json = JsonParser.parseReader(input.reader()).asJsonObject
                val refmap = json.get("refmap")?.asString ?: defaultRefmapPath
                val pkg = json.get("package").asString
                refmaps.computeIfAbsent(refmap) { JsonObject() }
                val mixins = (json.getAsJsonArray("mixins") ?: listOf()) +
                        (json.getAsJsonArray("client") ?: listOf()) +
                        (json.getAsJsonArray("server") ?: listOf())

                for (mixin in mixins) {
                    classesToRefmap.computeIfAbsent("$pkg.${mixin.asString}") { mutableSetOf() } += refmap
                }

                json.addProperty("refmap", refmap)
                val output = destinationDirectory.resolve(relativePath)
                output.parent.createDirectories()
                output.writeText(
                    GSON.toJson(json),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )
            } catch (e: Exception) {
                logger.error("Error while processing mixin config ($relativePath): ${e.message}")
            }
        } else if (refmapCheck(relativePath)) {
            try {
                logger.info("Found refmap: $relativePath")
                val json = JsonParser.parseReader(input.reader()).asJsonObject
                existingRefmaps[relativePath.toString()] = json
            } catch (e: Exception) {
                logger.error("Error while processing refmap ($relativePath): ${e.message}")
            }
        } else {
            throw IllegalStateException("Unexpected path: $relativePath")
        }
    }
}