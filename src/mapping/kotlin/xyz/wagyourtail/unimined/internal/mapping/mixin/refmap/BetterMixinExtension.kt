package xyz.wagyourtail.unimined.internal.mapping.mixin.refmap

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.OutputConsumerPath.ResourceRemapper
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
import xyz.wagyourtail.unimined.util.forEachInZip
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.function.Consumer
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.writeText

class BetterMixinExtension(
    val loggerLevel: LogLevel = LogLevel.WARN,
    val targets: Set<MixinExtension.AnnotationTarget> = MixinExtension.AnnotationTarget.values().toSet(),
    val fallbackWhenNotInJson: Boolean = false,
    val allowImplicitWildcards: Boolean = false,
):
        TinyRemapper.Extension,
        TinyRemapper.ApplyVisitorProvider,
        TinyRemapper.AnalyzeVisitorProvider,
        TinyRemapper.StateProcessor {
    private val tasks: MutableMap<Int, MutableList<Consumer<CommonData>>> = defaultedMapOf { mutableListOf() }

    private val defaultRefmapPath = mutableMapOf<InputTag?, String>()

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        fun translateLogLevel(loggerLevel: LogLevel) = when (loggerLevel) {
            LogLevel.DEBUG -> Logger.Level.INFO
            LogLevel.INFO -> Logger.Level.INFO
            LogLevel.WARN -> Logger.Level.WARN
            LogLevel.ERROR -> Logger.Level.ERROR
            LogLevel.QUIET -> Logger.Level.ERROR
            else -> Logger.Level.WARN
        }
    }

    val fallback = MixinExtension()

    var defaultRefmap = defaultedMapOf<InputTag?, _> { JsonObject() }
    val refmaps = defaultedMapOf<InputTag?, _> {
        mutableMapOf(defaultRefmapPath[it]!! to defaultRefmap[it]!!)
    }
    val classesToRefmap = defaultedMapOf<InputTag?, _> {
        mutableMapOf<String, MutableSet<String>>()
    }
    val mixinJsons = defaultedMapOf<InputTag?, _> {
        mutableMapOf<String, JsonObject>()
    }
    val existingRefmaps = defaultedMapOf<InputTag?, _> {
        mutableMapOf<String, JsonObject>()
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
        try {
            // detect if class in class lists
            val (tag, refmap) = synchronized(classesToRefmap) {
                classesToRefmap.entries.firstOrNull { (_, v) ->
                    v.containsKey(cls.name.replace("/", "."))
                }?.toPair() ?: (null to null)
            }

            return if (refmap != null) {
                logger.info("[RefmapTarget] Found mixin class: ${cls.name}")
                val refmapNames = refmap[cls.name.replace("/", ".")]
                val target = JsonObject()

                val existingRefmaps = refmapNames!!.mapNotNull { existingRefmaps[tag][it] }
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
                    combinedMappings,
                    onEnd = {
                        if (target.size() > 0) {
                            val refmaps = refmapNames.map { refmaps[tag][it]!! }
                            for (refmap in refmaps) {
                                if (!refmap.has("mappings")) {
                                    refmap.add("mappings", JsonObject())
                                }
                                val mappings = refmap.getAsJsonObject("mappings")
                                mappings.add(cls.name, target)
                            }
                        }
                    },
                    allowImplicitWildcards = allowImplicitWildcards
                )
            } else if (fallbackWhenNotInJson) {
                fallback.preApplyVisitor(cls, next)
            } else {
                object : ClassVisitor(Constant.ASM_VERSION, next) {
                    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
                        if (Annotation.MIXIN == descriptor) {
                            logger.error("[RefmapTarget] Found mixin class: ${cls.name}, but it is not in a mixin json file! This will cause issues and the mixin will not be remapped!")
                        }
                        return super.visitAnnotation(descriptor, visible)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error while processing class ${cls.name}: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private val hardFallbackFunction = fallback::class.java.getDeclaredMethod(
        "analyzeVisitor",
        Int::class.java,
        String::class.java,
        ClassVisitor::class.java
    ).apply {
        isAccessible = true
    }

    override fun insertAnalyzeVisitor(mrjVersion: Int, className: String, next: ClassVisitor?): ClassVisitor? {
        try {
            val (tag, refmap) = synchronized(classesToRefmap) {
                classesToRefmap.entries.firstOrNull { (_, v) ->
                    v.containsKey(className.replace("/", "."))
                }?.toPair() ?: (null to null)
            }

            if (refmap != null) {
                val refmapNames = refmap[className.replace("/", ".")]
                val existingRefmaps = refmapNames!!.mapNotNull { existingRefmaps[tag][it] }
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
                return hardFallbackFunction(fallback, mrjVersion, className, next) as ClassVisitor?
            } else {
                return object : ClassVisitor(Constant.ASM_VERSION, next) {
                    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                        if (Annotation.MIXIN == descriptor) {
                            logger.error("[HardTarget] Found mixin class: ${className}, but it is not in a mixin json file! This will cause issues and the mixin will not be remapped properly!")
                        }
                        return super.visitAnnotation(descriptor, visible)
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Error while processing class $className: ${e.javaClass.simpleName}: ${e.message}")
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
        write(null, fs)
    }

    fun write(tag: InputTag?, fs: FileSystem) {
        for ((path, refmap) in refmaps[tag]) {
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



    fun mixinCheck(relativePath: String): Boolean =
        relativePath.contains("mixins") && relativePath.endsWith(".json")

    fun refmapCheck(relativePath: String): Boolean =
        relativePath.contains("refmap") && relativePath.endsWith(".json")


    fun preRead(path: Path, defaultRefmap: String) {
        preRead(null, path, defaultRefmap)
    }

    fun preRead(tag: InputTag?, path: Path, defaultRefmap: String) {
        logger.info("[PreRead] Reading $path")
        defaultRefmapPath[tag] = defaultRefmap
        path.forEachInZip { file, input ->
            preReadIntl(tag, file, input)
        }
    }

    private fun preReadIntl(tag: InputTag?, file: String, input: InputStream) {
        if (refmapCheck(file)) {
            try {
                logger.info("[PreRead] Found refmap: $file")
                val json = JsonParser.parseReader(input.reader()).asJsonObject
                existingRefmaps[tag][file] = json
            } catch (e: Exception) {
                logger.error("[PreRead] Error while processing refmap ($file): ${e.message}")
            }
        } else if (mixinCheck(file)) {
            logger.info("[PreRead] Found mixin config: ${file.substringAfterLast("/")}")
            val json = JsonParser.parseReader(input.reader()).asJsonObject
            val refmap = json.get("refmap")?.asString ?: defaultRefmapPath[tag]!!
            val pkg = json.get("package").asString
            refmaps[tag].computeIfAbsent(refmap) { JsonObject() }
            val mixins = (json.getAsJsonArray("mixins") ?: listOf()) +
                    (json.getAsJsonArray("client") ?: listOf()) +
                    (json.getAsJsonArray("server") ?: listOf())

            logger.info("[PreRead]    ${mixins.size} mixins:")
            for (mixin in mixins) {
                synchronized(classesToRefmap) {
                    classesToRefmap[tag].computeIfAbsent("$pkg.${mixin.asString}") { mutableSetOf() } += refmap
                }
                logger.info("[PreRead]        $pkg.${mixin.asString}")
            }
            json.addProperty("refmap", refmap)
            mixinJsons[tag][file] = json
        }
    }

    fun resourceRemapper() = resourceReampper(null)

    fun resourceReampper(tag: InputTag?) = object : ResourceRemapper {
        override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
            return mixinCheck(relativePath.name) || refmapCheck(relativePath.name)
        }

        override fun transform(
            destinationDirectory: Path,
            relativePath: Path,
            input: InputStream,
            remapper: TinyRemapper
        ) {
            if (refmapCheck(relativePath.name)) {
                logger.info("[Transform] Found refmap: $relativePath")
            } else if (mixinCheck(relativePath.name)) {
                try {
                    val json = mixinJsons[tag][relativePath.toString()]!!
                    val output = destinationDirectory.resolve(relativePath)
                    output.parent.createDirectories()
                    output.writeText(
                        GSON.toJson(json),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
                } catch (e: Exception) {
                    logger.error("[Transform] Error while processing mixin config ($relativePath): ${e.message}")
                }
            } else {
                throw IllegalStateException("Unexpected path: $relativePath")
            }
        }

    }
}