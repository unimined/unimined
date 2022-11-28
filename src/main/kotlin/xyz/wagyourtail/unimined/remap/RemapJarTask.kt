package xyz.wagyourtail.unimined.remap

import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import net.faricmc.loom.util.kotlin.KotlinClasspathService
import net.faricmc.loom.util.kotlin.KotlinRemapperClassloader
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.UniminedExtensionImpl
import xyz.wagyourtail.unimined.providers.MinecraftProviderImpl
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.patch.fabric.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.providers.patch.forge.AccessTransformerMinecraftTransformer
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class RemapJarTask : Jar() {
    private val minecraftProvider = project.extensions.getByType(MinecraftProviderImpl::class.java)
    private val uniminedExtension = project.extensions.getByType(UniminedExtensionImpl::class.java)

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val sourceNamespace: Property<String>

    @get:Input
    abstract val fallbackFromNamespace: Property<String>

    @get:Input
    abstract val fallbackTargetNamespace: Property<String>

    @get:Input
    abstract val targetNamespace: Property<String>

    @get:Input
    @get:Optional
    abstract val minecraftTarget: Property<String>

    @get:Input
    @get:Optional
    abstract val remapATToLegacy: Property<Boolean>

    @get:Input
    @get:ApiStatus.Internal
    abstract val envType: Property<EnvType>

    init {
        sourceNamespace.convention(minecraftProvider.targetNamespace)
        fallbackFromNamespace.convention(minecraftProvider.mcRemapper.fallbackTarget)
        // TODO: fix, this doesn't work properly on fabric where some things get renamed to notch when they should inherit from parent class
        // was a hack to fix forge mappings missing a class in 1.14.4 causing failure with mojmap...
        fallbackTargetNamespace.convention("official")
        targetNamespace.convention(minecraftProvider.mcRemapper.fallbackTarget)
        remapATToLegacy.convention(false)
        envType.convention(EnvType.COMBINED)
        minecraftTarget.finalizeValueOnRead()
    }

    @TaskAction
    fun run() {
        if (targetNamespace.get() == sourceNamespace.get()) {
            Files.copy(
                inputFile.get().asFile.toPath(),
                outputs.files.files.first().toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            return
        }
        val env = if (minecraftTarget.isPresent) minecraftTarget.get() else if (minecraftProvider.disableCombined.get()) {
            envType.get().name
        } else {
            EnvType.COMBINED.name
        }
        val envType = EnvType.valueOf(env)
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(
                uniminedExtension.mappingsProvider.getMappingProvider(
                    envType,
                    sourceNamespace.get(),
                    fallbackFromNamespace.get(),
                    fallbackTargetNamespace.get(),
                    targetNamespace.get()
                )
            )
            .skipLocalVariableMapping(true)
            .ignoreConflicts(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .extension(MixinExtension())
        val classpath = KotlinClasspathService.getOrCreateIfRequired(project)
        if (classpath != null) {
            remapperB.extension(KotlinRemapperClassloader.create(classpath).tinyRemapperExtension)
        }
        minecraftProvider.mcRemapper.tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        val mc = minecraftProvider.mcRemapper.provider.getMinecraftWithMapping(
            envType,
            minecraftProvider.targetNamespace.get()
        )
        project.logger.lifecycle("Remapping output ${inputFile.get()} using $mc")
        project.logger.info("Environment: $envType")
        project.logger.info("Remap from: ${sourceNamespace.get()} to: ${targetNamespace.get()}")
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
                    AccessWidenerMinecraftTransformer.awRemapper(sourceNamespace.get(), targetNamespace.get()),
                    AccessTransformerMinecraftTransformer.atRemapper(remapATToLegacy.get())
                )
            )
            remapper.apply(it)
        }
        remapper.finish()

        minecraftProvider.minecraftTransformer.afterRemapJarTask(this, outputs.files.files.first().toPath())
    }


}