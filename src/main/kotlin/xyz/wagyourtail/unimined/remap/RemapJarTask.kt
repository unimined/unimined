package xyz.wagyourtail.unimined.remap

import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.UniminedExtension
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider

abstract class RemapJarTask : Jar() {
    private val minecraftProvider = project.extensions.getByType(MinecraftProvider::class.java)
    private val uniminedExtension = project.extensions.getByType(UniminedExtension::class.java)

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val sourceNamespace: Property<String>

    @get:Input
    abstract val fallbackFromNamespace: Property<String>

    @get:Input
    abstract val fallbackToNamespace: Property<String>

    @get:Input
    abstract val targetNamespace: Property<String>

    @get:Input
    @get:ApiStatus.Internal
    abstract val minecraftTarget: Property<String>

    init {
        sourceNamespace.convention(minecraftProvider.targetNamespace)
        fallbackFromNamespace.convention(minecraftProvider.mcRemapper.fallbackTarget)
        fallbackToNamespace.convention(minecraftProvider.mcRemapper.fallbackFrom)
        targetNamespace.convention(minecraftProvider.mcRemapper.fallbackTarget)
        minecraftTarget.convention(EnvType.COMBINED.name)
    }

    @TaskAction
    fun run() {
        val envType = EnvType.valueOf(minecraftTarget.get())
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(uniminedExtension.mappingsProvider.getMappingProvider(envType, sourceNamespace.get(), fallbackFromNamespace.get(), fallbackToNamespace.get(), targetNamespace.get()))
            .skipLocalVariableMapping(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .extension(MixinExtension())
        minecraftProvider.mcRemapper.tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        val mc = minecraftProvider.mcRemapper.provider.getMinecraftWithMapping(envType, minecraftProvider.targetNamespace.get())
        project.logger.warn("Remapping output ${inputFile.get()} using $mc")
        project.logger.warn("Environment: $envType")
        project.logger.warn("Remap from: ${sourceNamespace.get()} to: ${targetNamespace.get()}")
        remapper.readClassPathAsync(mc)
        remapper.readClassPathAsync(*minecraftProvider.mcRemapper.provider.mcLibraries.resolve().map { it.toPath() }.toTypedArray())

        remapper.readInputsAsync(inputFile.get().asFile.toPath())

        OutputConsumerPath.Builder(outputs.files.files.first().toPath()).build().use {
            it.addNonClassFiles(
                inputFile.get().asFile.toPath(),
            )
            remapper.apply(it)
        }
        remapper.finish()

    }


}