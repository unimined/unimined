package xyz.wagyourtail.unimined.internal.mapping.mixin.refmap

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.mappingio.format.ZipReader
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrEnvironment
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import net.fabricmc.tinyremapper.extension.mixin.common.Logger
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.gradle.api.logging.LogLevel
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import xyz.wagyourtail.unimined.util.defaultedMapOf
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.writeText

class BetterMixinExtension(
    var defaultRefmapPath: String,
    val loggerLevel: LogLevel = LogLevel.WARN,
    val targets: Set<MixinExtension.AnnotationTarget> = MixinExtension.AnnotationTarget.values().toSet(),
    val fallbackWhenNotInJson: Boolean = false
):
        TinyRemapper.Extension,
        TinyRemapper.ApplyVisitorProvider,
        TinyRemapper.AnalyzeVisitorProvider,
        TinyRemapper.StateProcessor,
        OutputConsumerPath.ResourceRemapper {
    private val tasks: MutableMap<Int, MutableList<Consumer<CommonData>>> = defaultedMapOf { mutableListOf() }

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

    val fallback = MixinExtension()

    var defaultRefmap = JsonObject()
    val refmaps = mutableMapOf(defaultRefmapPath to defaultRefmap)
    val classesToRefmap = mutableMapOf<String, MutableSet<String>>()
    val mixinJsons = mutableMapOf<String, JsonObject>()
    val existingRefmaps = mutableMapOf<String, JsonObject>()


    fun reset(defaultRefmapPath: String) {
        this.defaultRefmapPath = defaultRefmapPath
        tasks.clear()
        defaultRefmap = JsonObject()
        refmaps.clear()
        refmaps[defaultRefmapPath] = defaultRefmap
        classesToRefmap.clear()
        existingRefmaps.clear()

    }

    private val logger: Logger = Logger(translateLogLevel(loggerLevel))

    override fun attach(builder: TinyRemapper.Builder) {

        if (targets.contains(MixinExtension.AnnotationTarget.HARD)) {
            builder.extraAnalyzeVisitor(this)
            builder.extraStateProcessor(this)
        }

        if (targets.contains(MixinExtension.AnnotationTarget.SOFT)) {
            builder.extraPreApplyVisitor(this)
        }
    }

    override fun insertApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor {
        // detect if class in class lists
        return if (classesToRefmap.containsKey(cls.name.replace("/", "."))) {
            logger.info("[RefmapTarget] Found mixin class: ${cls.name}")
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
            MixinClassVisitorRefmapBuilder(
                CommonData(cls.environment, logger),
                cls.name,
                target,
                next,
                combinedMappings
            ) {
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
        } else if (fallbackWhenNotInJson) {
            fallback.preApplyVisitor(cls, next)
        } else {
            object: ClassVisitor(Constant.ASM_VERSION, next) {
                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
                    if (Annotation.MIXIN == descriptor) {
                        logger.error("[RefmapTarget] Found mixin class: ${cls.name}, but it is not in a mixin json file! This will cause issues and the mixin will not be remapped!")
                    }
                    return super.visitAnnotation(descriptor, visible)
                }
            }
        }
    }


    override fun insertAnalyzeVisitor(mrjVersion: Int, className: String, next: ClassVisitor?): ClassVisitor? {
        if (classesToRefmap.containsKey(className.replace("/", "."))) {

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
            logger.info("[HardTarget] Found mixin class: $className / $mrjVersion")
            return HarderTargetMixinClassVisitor(tasks[mrjVersion]!!, next, combinedMappings, logger)
        } else if (fallbackWhenNotInJson) {
            val fallbackFn = fallback::class.java.getDeclaredMethod(
                "analyzeVisitor",
                Int::class.java,
                String::class.java,
                ClassVisitor::class.java
            )
            fallbackFn.isAccessible = true
            return fallbackFn(fallback, mrjVersion, className, next) as ClassVisitor?
        } else {
            return object: ClassVisitor(Constant.ASM_VERSION, next) {
                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                    if (Annotation.MIXIN == descriptor) {
                        logger.error("[HardTarget] Found mixin class: ${className}, but it is not in a mixin json file! This will cause issues and the mixin will not be remapped properly!")
                    }
                    return super.visitAnnotation(descriptor, visible)
                }
            }
        }
    }

    override fun process(environment: TrEnvironment) {
        logger.info("Processing environment: ${environment.mrjVersion}")
        val data = CommonData(environment, logger)

        for (task in tasks[environment.mrjVersion]!!) {
            try {
                task.accept(data)
            } catch (e: RuntimeException) {
                logger.error(e.message)
            }
        }
        if (fallbackWhenNotInJson) {
            val fallbackFn = fallback::class.java.getDeclaredMethod("stateProcessor", TrEnvironment::class.java)
            fallbackFn.isAccessible = true
            fallbackFn(fallback, environment)
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
        relativePath.name.contains("mixins") && relativePath.extension == "json"

    fun refmapCheck(relativePath: Path): Boolean =
        relativePath.name.contains("refmap") && relativePath.extension == "json"

    override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
        return mixinCheck(relativePath) || refmapCheck(relativePath)
    }

    fun preRead(path: Path) {
        logger.info("[PreRead] Reading $path")
        ZipReader.forEachInZip(path) { file, input ->
            preRead(file, input)
        }
    }

    fun preRead(file: String, input: InputStream) {
        if (file.substringAfterLast("/").let { it.contains("mixins") && it.endsWith(".json")}) {
            logger.info("[PreRead] Found mixin config: ${file.substringAfterLast("/")}")
            val json = JsonParser.parseReader(input.reader()).asJsonObject
            val refmap = json.get("refmap")?.asString ?: defaultRefmapPath
            val pkg = json.get("package").asString
            refmaps.computeIfAbsent(refmap) { JsonObject() }
            val mixins = (json.getAsJsonArray("mixins") ?: listOf()) +
                    (json.getAsJsonArray("client") ?: listOf()) +
                    (json.getAsJsonArray("server") ?: listOf())

            logger.info("    ${mixins.size} mixins:")
            for (mixin in mixins) {
                classesToRefmap.computeIfAbsent("$pkg.${mixin.asString}") { mutableSetOf() } += refmap
                logger.info("        $pkg.${mixin.asString}")
            }
            json.addProperty("refmap", refmap)
            mixinJsons[file] = json
        }
    }

    override fun transform(
        destinationDirectory: Path,
        relativePath: Path,
        input: InputStream,
        remapper: TinyRemapper
    ) {
        if (refmapCheck(relativePath)) {
            try {
                logger.info("Found refmap: $relativePath")
                val json = JsonParser.parseReader(input.reader()).asJsonObject
                existingRefmaps[relativePath.toString()] = json
            } catch (e: Exception) {
                logger.error("Error while processing refmap ($relativePath): ${e.message}")
            }
        } else if (mixinCheck(relativePath)) {
            try {
                val json = mixinJsons[relativePath.toString()]!!
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
        } else {
            throw IllegalStateException("Unexpected path: $relativePath")
        }
    }
}