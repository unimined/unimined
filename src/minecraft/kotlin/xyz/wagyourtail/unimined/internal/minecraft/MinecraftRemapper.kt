package xyz.wagyourtail.unimined.internal.minecraft

import kotlinx.coroutines.runBlocking
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.OutputConsumerPath.ResourceRemapper
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.remap.MinecraftRemapConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.api.minecraft.MinecraftJar
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.getField
import java.nio.file.Path
import kotlin.io.path.*

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

    var extraResourceRemappers: (Namespace, Namespace) -> List<ResourceRemapper> by FinalizeOnRead { from, to -> listOf<ResourceRemapper>() }

    var extraExtensions by FinalizeOnRead { listOf<TinyRemapper.Extension>() }

    @Suppress("UNCHECKED_CAST")
    override fun addResourceRemapper(remapper: (Namespace, Namespace) -> ResourceRemapper) {
        val prev: FinalizeOnRead<(Namespace, Namespace) -> List<ResourceRemapper>> = MinecraftRemapper::class.getField("extraResourceRemappers")!!.getDelegate(this) as FinalizeOnRead<(Namespace, Namespace) -> List<ResourceRemapper>>
        val value = prev.value as (Namespace, Namespace) -> List<ResourceRemapper>
        extraResourceRemappers = { from, to -> value(from, to) + remapper(from, to) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun addExtension(extension: () -> TinyRemapper.Extension) {
        val prev: FinalizeOnRead<() -> List<TinyRemapper.Extension>> = MinecraftRemapper::class.getField("extraExtensions")!!.getDelegate(this) as FinalizeOnRead<() -> List<TinyRemapper.Extension>>
        val value = prev.value as () -> List<TinyRemapper.Extension>
        extraExtensions = { value() + extension() }
    }

    @ApiStatus.Internal
    fun provide(minecraft: MinecraftJar, remapTo: Namespace): MinecraftJar = runBlocking {
        if (remapTo == minecraft.mappingNamespace) return@runBlocking minecraft
        val mappingsId = provider.mappings.combinedNames()
        val parent = if (provider.mappings.hasStubs()) {
            provider.localCache.resolve("minecraft").resolve(mappingsId).createDirectories()
        } else {
            if (minecraft.parentPath.name != mappingsId) {
                minecraft.parentPath.resolve(mappingsId).createDirectories()
            } else {
                minecraft.parentPath
            }
        }
        val target = MinecraftJar(
            minecraft,
            parentPath = parent,
            mappingNamespace = remapTo,
        )

        if (target.path.exists() && !project.unimined.forceReload) {
            return@runBlocking target
        }

        project.logger.lifecycle("[Unimined/McRemapper] Remapping minecraft ${minecraft.envType} to $remapTo")
        var prevTarget = minecraft.path
        var prevNamespace = minecraft.mappingNamespace
        project.logger.info("[Unimined/McRemapper] $prevNamespace -> $remapTo")
        runBlocking {
            remapToInternal(prevTarget, target.path, minecraft.envType, prevNamespace, remapTo)
        }
        target
    }

    suspend fun remapToInternal(from: Path, target: Path, envType: EnvType, fromNs: Namespace, toNs: Namespace) {
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
        for (extraExtension in extraExtensions()) {
            remapperB.extension(extraExtension)
        }
        tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        remapper.readClassPathAsync(*provider.minecraftLibraries.files.map { it.toPath() }.toTypedArray())
        try {
            remapper.readInputsAsync(from)
            OutputConsumerPath.Builder(target).build().use {
                it.addNonClassFiles(
                    from,
                    remapper,
                    extraResourceRemappers(fromNs, toNs) + NonClassCopyMode.FIX_META_INF.remappers
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