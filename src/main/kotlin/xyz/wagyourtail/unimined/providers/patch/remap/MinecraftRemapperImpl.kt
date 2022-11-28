package xyz.wagyourtail.unimined.providers.patch.remap

import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.providers.MinecraftProviderImpl
import xyz.wagyourtail.unimined.providers.patch.MinecraftJar
import xyz.wagyourtail.unimined.providers.patch.forge.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.util.consumerApply
import kotlin.io.path.*

@Suppress("MemberVisibilityCanBePrivate")
class MinecraftRemapperImpl(
    val project: Project,
    val provider: MinecraftProviderImpl,
) {
    private val mappings by lazy { provider.parent.mappingsProvider }

    var fallbackTarget = "intermediary"
    var tinyRemapperConf: (TinyRemapper.Builder) -> Unit = {}

    @ApiStatus.Internal
    fun provide(minecraft: MinecraftJar, remapTo: String): MinecraftJar {
        return minecraft.let(consumerApply {
            val mappingsId = mappings.getCombinedNames(minecraft.envType)
            val parent = if (mappings.hasStubs(envType)) {
                provider.parent.getLocalCache().resolve("minecraft").resolve(mappingsId).createDirectories()
            } else {
                parentPath.resolve(mappingsId)
            }
            val target = MinecraftJar(
                minecraft,
                parentPath = parent,
                mappingNamespace = remapTo,
                fallbackNamespace = fallbackTarget
            )



            if (target.path.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                return@consumerApply target
            }

            val remapperB = TinyRemapper.newRemapper()
                .withMappings(
                    mappings.getMappingProvider(
                        envType,
                        mappingNamespace,
                        fallbackNamespace,
                        fallbackTarget,
                        remapTo
                    )
                )
                .renameInvalidLocals(true)
                .inferNameFromSameLvIndex(true)
                .threads(Runtime.getRuntime().availableProcessors())
                .checkPackageAccess(true)
                .fixPackageAccess(true)
                .rebuildSourceFilenames(true)
            tinyRemapperConf(remapperB)
            val remapper = remapperB.build()

            project.logger.lifecycle("Remapping minecraft to $remapTo")
            project.logger.info("Remapping ${path.name} to $target")
            project.logger.info("Mappings: $mappingNamespace to $remapTo")
            project.logger.info("Fallback: $fallbackNamespace to $fallbackTarget")

            try {
                remapper.readInputs(path)
                OutputConsumerPath.Builder(target.path).build().use {
                    it.addNonClassFiles(
                        path, remapper,
                        listOf(AccessTransformerMinecraftTransformer.atRemapper()) + NonClassCopyMode.FIX_META_INF.remappers
                    )
                    remapper.apply(it)
                }
            } catch (e: RuntimeException) {
                project.logger.warn("Failed to remap ${path.name} to $target")
                target.path.deleteIfExists()
                throw e
            }
            remapper.finish()
            target
        })
    }
}