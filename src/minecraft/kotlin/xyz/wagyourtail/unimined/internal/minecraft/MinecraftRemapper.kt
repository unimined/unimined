package xyz.wagyourtail.unimined.internal.minecraft

import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.remap.MinecraftRemapConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.consumerApply
import xyz.wagyourtail.unimined.util.getTempFilePath
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class MinecraftRemapper(val project: Project, val provider: MinecraftProvider): MinecraftRemapConfig() {

    override var tinyRemapperConf: TinyRemapper.Builder.() -> Unit by FinalizeOnRead {}

    override var replaceJSRWithJetbrains: Boolean by FinalizeOnRead(true)

    val MC_LV_PATTERN = Regex("\\$\\$\\d+")

    val JSR_TO_JETBRAINS = mapOf(
        "javax/annotation/Nullable" to "org/jetbrains/annotations/Nullable",
        "javax/annotation/Nonnull" to "org/jetbrains/annotations/NotNull",
        "javax/annotation/concurrent/Immutable" to "org/jetbrains/annotations/Unmodifiable"
    )

    override fun config(remapperBuilder: TinyRemapper.Builder.() -> Unit) {
        tinyRemapperConf = remapperBuilder
    }

    @ApiStatus.Internal
    fun provide(minecraft: MinecraftJar, remapTo: MappingNamespace, remapFallback: MappingNamespace): MinecraftJar {
        if (remapTo == minecraft.mappingNamespace && remapFallback == minecraft.fallbackNamespace) return minecraft
        return minecraft.let(consumerApply {
            val mappingsId = provider.mappings.combinedNames
            val parent = if (provider.mappings.hasStubs) {
                project.unimined.getLocalCache().resolve("minecraft").resolve(mappingsId).createDirectories()
            } else {
                parentPath.resolve(mappingsId)
            }
            val target = MinecraftJar(
                minecraft,
                parentPath = parent,
                mappingNamespace = remapTo,
                fallbackNamespace = remapFallback
            )

            if (target.path.exists() && !project.unimined.forceReload) {
                return@consumerApply target
            }

            val path = MappingNamespace.calculateShortestRemapPathWithFallbacks(
                mappingNamespace,
                fallbackNamespace,
                remapFallback,
                remapTo,
                provider.mappings.available
            )
            if (path.isEmpty()) {
                if (minecraft.path != target.path) {
                    minecraft.path.copyTo(target.path, overwrite = true)
                }
                return@consumerApply target
            }
            val last = path.last()
            project.logger.lifecycle("[Unimined/McRemapper] Remapping minecraft $envType to $remapTo")
            var prevTarget = minecraft.path
            var prevNamespace = minecraft.mappingNamespace
            for (step in path) {
                project.logger.info("[Unimined/McRemapper] $prevNamespace -> $step")
                val targetFile = if (step == last) {
                    target.path
                } else if (step.first) {
                    getTempFilePath("remap-mc-inner-${step.second.namespace}", ".jar")
                } else {
                    MinecraftJar(
                        minecraft,
                        parentPath = parent,
                        mappingNamespace = step.second,
                        fallbackNamespace = prevNamespace
                    ).path
                }
                remapToInternal(prevTarget, targetFile, envType, prevNamespace, step.second)
                prevTarget = targetFile
                prevNamespace = step.second
                project.logger.info("[Unimined/McRemapper]    $targetFile")
            }
            target
        })
    }

    fun remapToInternal(from: Path, target: Path, envType: EnvType, fromNs: MappingNamespace, toNs: MappingNamespace) {
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(
                provider.mappings.getTRMappings(
                    fromNs to toNs,
                    true
                )
            )
            .threads(Runtime.getRuntime().availableProcessors())
            .renameInvalidLocals(true)
            .rebuildSourceFilenames(true)
            .invalidLvNamePattern(MC_LV_PATTERN.toPattern())
            .inferNameFromSameLvIndex(true)
            .checkPackageAccess(true)
            .fixPackageAccess(true)
        if (replaceJSRWithJetbrains) {
            remapperB.withMappings { JSR_TO_JETBRAINS.forEach(it::acceptClass) }
        }
        tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        remapper.readClassPathAsync(*provider.minecraftLibraries.files.map { it.toPath() }.toTypedArray())
        try {
            remapper.readInputsAsync(from)
            OutputConsumerPath.Builder(target).build().use {
                it.addNonClassFiles(
                    from, remapper,
                    listOf(AccessTransformerMinecraftTransformer.AtRemapper(project.logger)) + NonClassCopyMode.FIX_META_INF.remappers
                )
                remapper.apply(it)
            }
            remapper.finish()
        } catch (e: RuntimeException) {
            project.logger.warn("[Unimined/McRemapper] Failed to remap $from to $target in $envType")
            target.deleteIfExists()
            throw e
        }
    }
}