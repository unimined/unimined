package xyz.wagyourtail.unimined.minecraft.patch.remap

import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.minecraft.transform.remap.MinecraftRemapper
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.minecraft.patch.forge.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.util.consumerApply
import xyz.wagyourtail.unimined.util.getTempFilePath
import java.nio.file.Path
import kotlin.io.path.*

@Suppress("MemberVisibilityCanBePrivate")
class MinecraftRemapperImpl(
    val project: Project,
    val provider: MinecraftProviderImpl,
): MinecraftRemapper() {
    private val mappings by lazy { project.mappings }

    val MC_LV_PATTERN = Regex("\\$\\$\\d+")

    val JSR_TO_JETBRAINS = mapOf(
        "javax/annotation/Nullable" to "org/jetbrains/annotations/Nullable",
        "javax/annotation/Nonnull" to "org/jetbrains/annotations/NotNull",
        "javax/annotation/concurrent/Immutable" to "org/jetbrains/annotations/Unmodifiable"
    )

    @ApiStatus.Internal
    fun provide(minecraft: MinecraftJar, remapTo: MappingNamespace, remapFallback: MappingNamespace): MinecraftJar {
        if (remapTo == minecraft.mappingNamespace && remapFallback == minecraft.fallbackNamespace) return minecraft
        return minecraft.let(consumerApply {
            val mappingsId = mappings.getCombinedNames(minecraft.envType)
            val parent = if (mappings.hasStubs(envType)) {
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

            if (target.path.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                return@consumerApply target
            }

            val path = MappingNamespace.calculateShortestRemapPathWithFallbacks(
                mappingNamespace,
                fallbackNamespace,
                remapFallback,
                remapTo,
                mappings.getAvailableMappings(project.minecraft.defaultEnv)
            )
            if (path.isEmpty()) {
                if (minecraft.path != target.path) {
                    minecraft.path.copyTo(target.path, overwrite = true)
                }
                return@consumerApply target
            }
            val last = path.last()
            project.logger.lifecycle("Remapping minecraft $envType to $remapTo")
            var prevTarget = minecraft.path
            var prevNamespace = minecraft.mappingNamespace
            for (step in path) {
                project.logger.info("  $step")
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
            }
            target
        })
    }

    fun remapToInternal(from: Path, target: Path, envType: EnvType, fromNs: MappingNamespace, toNs: MappingNamespace) {
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(
                mappings.getMappingsProvider(
                    envType,
                    fromNs to toNs
                )
            )
            .withMappings { JSR_TO_JETBRAINS.forEach(it::acceptClass) }
            .threads(Runtime.getRuntime().availableProcessors())
            .renameInvalidLocals(true)
            .rebuildSourceFilenames(true)
            .invalidLvNamePattern(MC_LV_PATTERN.toPattern())
            .inferNameFromSameLvIndex(true)
            .checkPackageAccess(true)
            .fixPackageAccess(true)
        tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        remapper.readClassPathAsync(*provider.mcLibraries.resolve().map { it.toPath() }.toTypedArray())
        try {
            remapper.readInputsAsync(from)
            OutputConsumerPath.Builder(target).build().use {
                it.addNonClassFiles(
                    from, remapper,
                    listOf(AccessTransformerMinecraftTransformer.atRemapper(project.logger)) + NonClassCopyMode.FIX_META_INF.remappers
                )
                remapper.apply(it)
            }
            remapper.finish()
        } catch (e: RuntimeException) {
            project.logger.warn("Failed to remap $from to $target in $envType")
            target.deleteIfExists()
            throw e
        }


    }
}