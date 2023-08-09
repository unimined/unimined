package xyz.wagyourtail.unimined.internal.mapping.extension

import com.google.gson.JsonObject
import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrEnvironment
import net.fabricmc.tinyremapper.extension.mixin.common.Logger
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData
import org.gradle.api.logging.LogLevel
import org.jetbrains.annotations.ApiStatus
import org.objectweb.asm.ClassVisitor
import xyz.wagyourtail.unimined.api.mapping.mixin.MixinRemapOptions
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.OfficialMixinMetaData
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.BaseMixinHard
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.HardTargetRemappingClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.BaseMixinRefmap
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixinextra.MixinExtra
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.defaultedMapOf
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class MixinRemapExtension(
    loggerLevel: LogLevel = LogLevel.WARN,
    allowImplicitWildcards: Boolean = false,
) : PerInputTagExtension<MixinRemapExtension.MixinTarget>(), MixinRemapOptions {

    val allowImplicitWildcards by FinalizeOnRead(allowImplicitWildcards)

    override fun register(tag: InputTag?): MixinTarget {
        return MixinTarget(tag, this).apply {
            metadataReader.forEach {
                this.addMetadata(it(this@MixinRemapExtension))
            }
        }
    }

    private var metadataReader = mutableListOf<(MixinRemapExtension) -> MixinMetadata>()
    private var modifyHardRemapper: (HardTargetRemappingClassVisitor) -> Unit = {}
    private var modifyRefmapBuilder: (RefmapBuilderClassVisitor) -> Unit = {}


    val logger: Logger = Logger(
        translateLogLevel(
            loggerLevel
        )
    )

    @ApiStatus.Internal
    fun modifyMetadataReader(modifier: (MixinRemapExtension) -> MixinMetadata) {
        metadataReader.add(modifier)
    }

    @ApiStatus.Internal
    fun modifyHardRemapper(modifier: (HardTargetRemappingClassVisitor) -> Unit) {
        val old = modifyHardRemapper
        modifyHardRemapper = { old(it); modifier(it) }
    }

    @ApiStatus.Internal
    fun modifyRefmapBuilder(modifier: (RefmapBuilderClassVisitor) -> Unit) {
        val old = modifyRefmapBuilder
        modifyRefmapBuilder = { old(it); modifier(it) }
    }

    override fun enableMixinExtra() {
        modifyRefmapBuilder(MixinExtra::refmapBuilder)
    }

    override fun enableBaseMixin() {
        modifyMetadataReader(::OfficialMixinMetaData)
        modifyHardRemapper(BaseMixinHard::hardRemapper)
        modifyRefmapBuilder(BaseMixinRefmap::refmapBuilder)
    }

    override fun enableJarModAgent() {
        TODO()
    }


    fun resetMetadataReader() {
        metadataReader = mutableListOf()
    }

    fun resetHardRemapper() {
        modifyHardRemapper = {}
    }

    fun resetRefmapBuilder() {
        modifyRefmapBuilder = {}
    }

    override fun reset() {
        resetMetadataReader()
        resetHardRemapper()
        resetRefmapBuilder()
    }


    class MixinTarget(tag: InputTag?, val extension: MixinRemapExtension) : InputTagExtension {
        protected val metadata: MergedMetadata = MergedMetadata(extension)
        protected val tasks: MutableMap<Int, MutableList<(CommonData) -> Unit>> = defaultedMapOf { mutableListOf() }

        fun addMetadata(metadata: MixinMetadata) {
            this.metadata.addMetadata(metadata)
        }

        override fun readInput(remapper: TinyRemapper, vararg input: Path): CompletableFuture<*> {
            return metadata.readInput(*input).thenComposeAsync { super.readInput(remapper, *input) }
        }

        override fun analyzeVisitor(mrjVersion: Int, className: String, next: ClassVisitor): ClassVisitor {
            try {
                return if (metadata.contains(dot(className))) {
                    val refmap = metadata.getExistingRefmapFor(dot(className))
                    val existing = refmap?.get("mappings")?.asJsonObject?.get(className)?.asJsonObject ?: JsonObject()
                    val mappings = mutableMapOf<String, String>()
                    existing.entrySet().forEach {
                        mappings[it.key] = it.value.asString
                    }
                    extension.logger.info("[HardTarget] found mixin class $className")
                    if (mappings.isNotEmpty()) {
                        extension.logger.info("[HardTarget] existing mappings $mappings")
                    }
                    val visitor = HardTargetRemappingClassVisitor(
                        next,
                        className,
                        mappings,
                        extension.logger
                    )
                    extension.modifyHardRemapper(visitor)
                    tasks[mrjVersion]!!.add(visitor::runRemap)
                    visitor
                } else {
                    next
                }
            } catch (e: Exception) {
                throw IllegalStateException("Error while processing class $className: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }

        override fun stateProcessor(environment: TrEnvironment) {
            extension.logger.info("[HardTarget] processing state for ${environment.mrjVersion}")
            val data = CommonData(environment, extension.logger)
            try {
                for (task in tasks[environment.mrjVersion]!!) {
                    task(data)
                }
            } catch (e: Exception) {
                throw IllegalStateException("Error while processing state for ${environment.mrjVersion}: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }

        override fun preApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor {
            try {
                return if (metadata.contains(dot(cls.name))) {
                    val refmap = metadata.getExistingRefmapFor(dot(cls.name))
                    val existing = refmap?.get("mappings")?.asJsonObject?.get(cls.name)?.asJsonObject ?: JsonObject()
                    val mappings = mutableMapOf<String, String>()
                    existing.entrySet().forEach {
                        mappings[it.key] = it.value.asString
                    }
                    extension.logger.info("[RefmapBuilder] found mixin class ${cls.name}")
                    if (mappings.isNotEmpty()) {
                        extension.logger.info("[RefmapBuilder] existing mappings $mappings")
                    }
                    val target = JsonObject()
                    val visitor = RefmapBuilderClassVisitor(
                        CommonData(cls.environment, extension.logger),
                        cls.name,
                        target,
                        next,
                        mappings,
                        onEnd = {
                            if (target.size() > 0) {
                                extension.logger.info("[RefmapBuilder] adding ${target.size()} mappings for ${cls.name}")
                                val refmap = metadata.getRefmapFor(dot(cls.name))
                                if (!refmap.has("mappings")) {
                                    refmap.add("mappings", JsonObject())
                                }
                                val mappings = refmap.get("mappings").asJsonObject
                                mappings.add(cls.name, target)
                            }
                        },
                        allowImplicitWildcards = extension.allowImplicitWildcards
                    )
                    extension.modifyRefmapBuilder(visitor)
                    visitor
                } else {
                    next
                }
            } catch (e: Exception) {
                throw IllegalStateException("Error while processing class ${cls.name}: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }

        override fun insertExtra(fs: FileSystem) {
            metadata.writeExtra(fs)
        }

    }

    abstract class MixinMetadata(val parent: MixinRemapExtension) {

        abstract fun readInput(vararg input: Path): CompletableFuture<*>

        abstract fun contains(className: String): Boolean
        abstract fun getRefmapFor(className: String): JsonObject

        abstract fun writeExtra(fs: FileSystem)
        abstract fun getExistingRefmapFor(className: String): JsonObject?
    }

    class MergedMetadata(parent: MixinRemapExtension) : MixinMetadata(parent) {
        val metadata: MutableSet<MixinMetadata> = mutableSetOf()

        fun addMetadata(metadata: MixinMetadata) {
            this.metadata.add(metadata)
        }

        override fun readInput(vararg input: Path): CompletableFuture<*> {
            return CompletableFuture.allOf(*metadata.map { it.readInput(*input) }.toTypedArray())
        }

        override fun contains(className: String): Boolean {
            return metadata.any { it.contains(className) }
        }

        override fun getRefmapFor(className: String): JsonObject {
            return metadata.first { it.contains(className) }.getRefmapFor(className)
        }

        override fun writeExtra(fs: FileSystem) {
            metadata.forEach { it.writeExtra(fs) }
        }

        override fun getExistingRefmapFor(className: String): JsonObject? {
            return metadata.firstOrNull { it.contains(className) }?.getExistingRefmapFor(className)
        }

    }

    companion object {
        fun dot(name: String): String = name.replace('/', '.')


        fun translateLogLevel(loggerLevel: LogLevel) = when (loggerLevel) {
            LogLevel.DEBUG -> Logger.Level.INFO
            LogLevel.INFO -> Logger.Level.INFO
            LogLevel.WARN -> Logger.Level.WARN
            LogLevel.ERROR -> Logger.Level.ERROR
            LogLevel.QUIET -> Logger.Level.ERROR
            else -> Logger.Level.WARN
        }
    }

}