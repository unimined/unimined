package xyz.wagyourtail.unimined.remap

import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.mappingio.format.ZipReader
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.UniminedExtensionImpl
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.tasks.RemapJarTask
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.fabric.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.patch.forge.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.refmap.RefmapBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class RemapJarTaskImpl : RemapJarTask() {
    private val minecraftProvider = project.extensions.getByType(MinecraftProviderImpl::class.java)
    private val uniminedExtension = project.extensions.getByType(UniminedExtensionImpl::class.java)

    @TaskAction
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    fun run() {

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

        if (remapThroughOfficial.get()) {
            val temp = File.createTempFile("output-official", ".jar")
            temp.delete()
            project.logger.info("remapping through official jar")

            remap(devNs, devFNs, "official", "official", inputFile.asFile.get(), temp)
            temp.deleteOnExit()
            remap("official", "official", prodFNs, prodNs, temp, outputs.files.files.first())
        } else {
            remap(devNs, devFNs, prodFNs, prodNs, inputFile.asFile.get(), outputs.files.files.first())
        }

        minecraftProvider.mcPatcher.afterRemapJarTask(outputs.files.files.first().toPath())
    }

    private fun remap(from: String, fromFallback: String, toFallback: String, to: String, input: File, output: File) {
        val env = if (minecraftProvider.disableCombined.get()) {
            envType.get().name
        } else {
            EnvType.COMBINED.name
        }

        val envType = EnvType.valueOf(env)
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(
                uniminedExtension.mappingsProvider.getMappingProvider(
                    envType,
                    from,
                    fromFallback,
                    toFallback,
                    to
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
            from,
            fromFallback
        )
        project.logger.lifecycle("Remapping jar ${input} using $mc")
        project.logger.lifecycle("output ${output}")
        project.logger.info("Environment: $envType")
        project.logger.info("Remap from: $from to: $to")
        project.logger.info("Fallback from: $fromFallback to: $toFallback")
        remapper.readClassPathAsync(mc)
        remapper.readClassPathAsync(
            *minecraftProvider.mcRemapper.provider.mcLibraries.resolve()
                .map { it.toPath() }
                .toTypedArray()
        )

        remapper.readInputsAsync(input.toPath())

        OutputConsumerPath.Builder(output.toPath()).build().use {
            it.addNonClassFiles(
                input.toPath(),
                remapper,
                listOf(
                    AccessWidenerMinecraftTransformer.awRemapper(from, to),
                    AccessTransformerMinecraftTransformer.atRemapper(remapATToLegacy.get()),
                    refmapBuilder
                )
            )
            remapper.apply(it)
        }
        remapper.finish()

        ZipReader.openZipFileSystem(output.toPath(), mapOf("mutable" to true)).use {
            refmapBuilder.write(it)
        }
    }

}