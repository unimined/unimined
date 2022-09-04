package xyz.wagyourtail.unimined.providers.minecraft.patch.remap

import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.consumerApply
import xyz.wagyourtail.unimined.maybeCreate
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.patch.MinecraftJar
import java.nio.file.Path
import kotlin.io.path.*

@Suppress("MemberVisibilityCanBePrivate")
class MinecraftRemapper(
    val project: Project,
    val provider: MinecraftProvider,
) {
    private val mappings by lazy { provider.parent.mappingsProvider }

    var fallbackTarget = "intermediary"
    var tinyRemapperConf: (TinyRemapper.Builder) -> Unit = {}

    @ApiStatus.Internal
    fun provide(minecraft: MinecraftJar, remapTo: String, skipMappingId: Boolean = false): Path {
        return minecraft.let(consumerApply {
            val parent = if (mappings.hasStubs(envType)) {
                provider.parent.getLocalCache().resolve("minecraft").maybeCreate()
            } else {
                jarPath.parent
            }
            val target = if (skipMappingId) {
                parent.resolve(mappings.getCombinedNames(envType))
                    .resolve("${jarPath.nameWithoutExtension}-${remapTo}.${jarPath.extension}")
            } else {
                parent.resolve(mappings.getCombinedNames(envType))
                    .resolve("${jarPath.nameWithoutExtension}-mapped-${mappings.getCombinedNames(envType)}-${remapTo}.${jarPath.extension}")
            }



            if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                return@consumerApply target
            }

            val remapperB = TinyRemapper.newRemapper()
                .withMappings(
                    mappings.getMappingProvider(
                        envType,
                        mappingNamespace,
                        fallbackMappingNamespace,
                        fallbackTarget,
                        remapTo
                    )
                )
                .renameInvalidLocals(true)
                .inferNameFromSameLvIndex(true)
                .propagateBridges(TinyRemapper.LinkedMethodPropagation.ENABLED)
                .propagateRecordComponents(TinyRemapper.LinkedMethodPropagation.ENABLED)
                .threads(Runtime.getRuntime().availableProcessors())
                .checkPackageAccess(true)
                .fixPackageAccess(true)
                .rebuildSourceFilenames(true)
            tinyRemapperConf(remapperB)
            val remapper = remapperB.build()

            project.logger.warn("Remapping ${jarPath.name} to $target")

            try {
                OutputConsumerPath.Builder(target).build().use {
                    it.addNonClassFiles(jarPath, NonClassCopyMode.FIX_META_INF, null)
                    remapper.readInputs(jarPath)
                    remapper.apply(it)
                }
            } catch (e: RuntimeException) {
                project.logger.warn("Failed to remap ${jarPath.name} to $target")
                target.deleteIfExists()
                throw e
            }
            remapper.finish()
            target
        })
    }
}