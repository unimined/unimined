package xyz.wagyourtail.unimined.output.remap

import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.mappingio.format.ZipReader
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.mappings.mappings
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.fabric.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.forge.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.refmap.BetterMixinExtension
import xyz.wagyourtail.unimined.util.getTempFilePath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

abstract class RemapJarTaskImpl: RemapJarTask() {
    private val minecraftProvider = project.extensions.getByType(MinecraftProviderImpl::class.java)
    private val uniminedExtension = project.extensions.getByType(UniminedExtension::class.java)

    @TaskAction
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    fun run() {
        val prodNs = targetNamespace.getOrElse(minecraftProvider.mcPatcher.prodNamespace)!!
        val devNs = sourceNamespace.getOrElse(minecraftProvider.mcPatcher.devNamespace)!!
        val devFNs = fallbackFromNamespace.getOrElse(minecraftProvider.mcPatcher.devFallbackNamespace)!!

        val env = envType.getOrElse(project.minecraft.defaultEnv)!!

        val path = MappingNamespace.calculateShortestRemapPathWithFallbacks(
            devNs,
            devFNs,
            prodNs,
            prodNs,
            project.mappings.getAvailableMappings(env)
        )

        if (devNs == prodNs || path.isEmpty()) {
            Files.copy(
                inputFile.get().asFile.toPath(),
                outputs.files.files.first().toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            return
        }

        val last = path.last()
        project.logger.lifecycle("remapping output ${inputFile.get().asFile.name} from $devNs to $prodNs")
        var prevTarget = inputFile.get().asFile.toPath()
        var prevNamespace = devNs
        var prevPrevNamespace: MappingNamespace? = null
        for (i in path.indices) {
            val step = path[i]
            val nextTarget = if (step != last)
                getTempFilePath("${inputFile.get().asFile.nameWithoutExtension}-temp-${step.second.namespace}", ".jar")
            else
                outputs.files.files.first().toPath()
            val mcNamespace = prevNamespace
            val mcFallbackNamespace = if (step.first) {
                step.second
            } else {
                prevPrevNamespace
            }
            val mc = minecraftProvider.getMinecraftWithMapping(
                env,
                mcNamespace,
                mcFallbackNamespace!!
            )
            remapToInternal(prevTarget, nextTarget, env, prevNamespace, step.second, mc)
            prevTarget = nextTarget
            prevPrevNamespace = prevNamespace
            prevNamespace = step.second
        }

        minecraftProvider.mcPatcher.afterRemapJarTask(this, outputs.files.files.first().toPath())
    }

    protected fun remapToInternal(
        from: Path,
        target: Path,
        envType: EnvType,
        fromNs: MappingNamespace,
        toNs: MappingNamespace,
        mc: Path
    ) {
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(
                uniminedExtension.mappingsProvider.getMappingsProvider(
                    envType,
                    fromNs to toNs,
                    false
                )
            )
            .skipLocalVariableMapping(true)
            .ignoreConflicts(true)
            .threads(Runtime.getRuntime().availableProcessors())
        val classpath = KotlinClasspathService.getOrCreateIfRequired(project)
        if (classpath != null) {
            remapperB.extension(KotlinRemapperClassloader.create(classpath).tinyRemapperExtension)
        }
        val betterMixinExtension = BetterMixinExtension(
            "${project.rootProject.name}.refmap.json",
            project.gradle.startParameter.logLevel
        )
        remapperB.extension(betterMixinExtension)
        project.minecraft.mcRemapper.tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        remapper.readClassPathAsync(
            *sourceSet.runtimeClasspath.files.map { it.toPath() }
                .filter { !minecraftProvider.isMinecraftJar(it) }
                .filter { it.exists() }
                .toTypedArray()
        )
        remapper.readClassPathAsync(mc)

        remapper.readInputsAsync(from)

        try {
            OutputConsumerPath.Builder(target).build().use {
                it.addNonClassFiles(
                    from,
                    remapper,
                    listOf(
                        AccessWidenerMinecraftTransformer.awRemapper(fromNs.namespace, toNs.namespace),
                        AccessTransformerMinecraftTransformer.atRemapper(project.logger, remapATToLegacy.get()),
                        betterMixinExtension
                    )
                )
                remapper.apply(it)
            }
        } catch (e: Exception) {
            target.deleteIfExists()
            throw e
        }
        remapper.finish()

        ZipReader.openZipFileSystem(target, mapOf("mutable" to true)).use {
            betterMixinExtension.write(it)
        }

    }

}