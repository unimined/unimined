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
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider

abstract class RemapJarTask : Jar() {
    private val minecraftProvider = project.extensions.getByType(MinecraftProvider::class.java)

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val sourceNamespace: Property<String>

    @get:Input
    abstract val targetNamespace: Property<String>

    init {
        sourceNamespace.convention(minecraftProvider.targetNamespace)
        targetNamespace.convention(minecraftProvider.mcRemapper.fallbackTarget)
    }

    @TaskAction
    fun run() {
        val remapper = TinyRemapper.newRemapper()
            .withMappings(minecraftProvider.mcRemapper.getMappingProvider(sourceNamespace.get(), targetNamespace.get(), targetNamespace.get(), minecraftProvider.mcRemapper.mappingTree,false))
            .skipLocalVariableMapping(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .extension(MixinExtension())
            .build()

        val mc = minecraftProvider.mcRemapper.provider.getMinecraftCombinedWithMapping(minecraftProvider.targetNamespace.get())
        remapper.readClassPathAsync(mc)
        project.logger.warn("Remapping mods using $mc")
        remapper.readClassPathAsync(*minecraftProvider.mcRemapper.provider.mcLibraries.resolve().map { it.toPath() }.toTypedArray())

        remapper.readInputs(inputFile.get().asFile.toPath())

        OutputConsumerPath.Builder(outputs.files.files.first().toPath()).build().use {
            it.addNonClassFiles(
                inputFile.get().asFile.toPath(),
            )
            remapper.apply(it)
        }

        remapper.finish()

    }


}