package xyz.wagyourtail.unimined.remap

import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.mappingio.format.ZipReader
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.UniminedExtensionImpl
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.tasks.RemapJarTask
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.fabric.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.forge.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.refmap.RefmapBuilder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class RemapJarTaskImpl : RemapJarTask() {
    private val minecraftProvider = project.extensions.getByType(MinecraftProviderImpl::class.java)
    private val uniminedExtension = project.extensions.getByType(UniminedExtensionImpl::class.java)

    @TaskAction
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    fun run() {
        val env = if (minecraftProvider.disableCombined.get()) {
            envType.get().name
        } else {
            EnvType.COMBINED.name
        }

        val prodNs = targetNamespace.getOrElse(minecraftProvider.mcPatcher.prodNamespace)!!
        val prodFNs = fallbackTargetNamespace.getOrElse(minecraftProvider.mcPatcher.prodFallbackNamespace)!!
        val devNs = sourceNamespace.getOrElse(minecraftProvider.mcPatcher.devNamespace)!!
        val devFNs = fallbackFromNamespace.getOrElse(minecraftProvider.mcPatcher.devFallbackNamespace)!!

        if (prodNs == devNs && prodFNs == devFNs) {
            Files.copy(
                inputFile.get().asFile.toPath(),
                outputs.files.files.first().toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            return
        }

        val envType = EnvType.valueOf(env)
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(
                uniminedExtension.mappingsProvider.getMappingProvider(
                    envType,
                    devNs,
                    devFNs,
                    prodFNs,
                    prodNs
                )
            )
            .skipLocalVariableMapping(true)
            .ignoreConflicts(true)
            .threads(Runtime.getRuntime().availableProcessors())
        val classpath = KotlinClasspathService.getOrCreateIfRequired(project)
        if (classpath != null) {
            remapperB.extension(KotlinRemapperClassloader.create(classpath).tinyRemapperExtension)
        }
        val projectPath = if (project.name == ":") "" else project.name.replace(":", "-")
        val refmapBuilder = RefmapBuilder("${project.rootProject.name}${projectPath}.refmap.json", project.gradle.startParameter.logLevel)
        remapperB.extension(refmapBuilder)
        minecraftProvider.mcRemapper.tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        val mc = minecraftProvider.mcRemapper.provider.getMinecraftWithMapping(
            envType,
            devNs,
            devFNs
        )
        project.logger.lifecycle("Remapping output ${inputFile.get()} using $mc")
        project.logger.info("Environment: $envType")
        project.logger.info("Remap from: $devNs to: $prodNs")
        project.logger.info("Fallback from: $devFNs to: $prodFNs")
        remapper.readClassPathAsync(mc)
        remapper.readClassPathAsync(
            *minecraftProvider.mcRemapper.provider.mcLibraries.resolve()
                .map { it.toPath() }
                .toTypedArray()
        )

        remapper.readInputsAsync(inputFile.get().asFile.toPath())

        OutputConsumerPath.Builder(outputs.files.files.first().toPath()).build().use {
            it.addNonClassFiles(
                inputFile.get().asFile.toPath(),
                remapper,
                listOf(
                    AccessWidenerMinecraftTransformer.awRemapper(devNs, prodNs),
                    AccessTransformerMinecraftTransformer.atRemapper(remapATToLegacy.get()),
                    refmapBuilder
                )
            )
            remapper.apply(it)
        }
        remapper.finish()

        ZipReader.openZipFileSystem(outputs.files.files.first().toPath(), mapOf("mutable" to true)).use {
            refmapBuilder.write(it)
        }

        minecraftProvider.mcPatcher.afterRemapJarTask(outputs.files.files.first().toPath())
    }


}