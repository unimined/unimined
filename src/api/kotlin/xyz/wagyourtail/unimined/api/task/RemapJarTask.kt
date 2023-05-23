package xyz.wagyourtail.unimined.api.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.EnvType

/**
 * task responsible for transforming your built jar to production.
 * @since 0.1.0
 */
@Suppress("LeakingThis")
abstract class RemapJarTask : Jar() {

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    /**
     * the dev env mappings
     */
    @get:Input
    @get:Optional
    @get:ApiStatus.Internal
    abstract val devNamespace: Property<MappingNamespace?>

    /**
     * the dev env fallback mappings
     * defaults to {@link mcConfig.mappings.devNamespace}
     */
    @get:Input
    @get:Optional
    @get:ApiStatus.Internal
    abstract val devFallbackNamespace: Property<MappingNamespace?>

    /**
     * the prod env mappings
     * defaults to {@link mcConfig.mcProvider.prodNamespace}
     */
    @get:Input
    @get:Optional
    @get:ApiStatus.Internal
    abstract val prodNamespace: Property<MappingNamespace?>

    /**
     * whether to remap AccessTransformers to the legacy format (<=1.7.10)
     */
    @get:Input
    @get:Optional
    abstract val remapATToLegacy: Property<Boolean?>

    /**
     * env type to remap against
     */
    @get:Input
    @get:Optional
    @get:ApiStatus.Internal
    abstract val envType: Property<EnvType?>

    fun setSource(namespace: String) {
        devNamespace.set(MappingNamespace.getNamespace(namespace))
    }

    fun setFallbackSource(namespace: String) {
        devFallbackNamespace.set(MappingNamespace.getNamespace(namespace))
    }

    fun setTarget(namespace: String) {
        prodNamespace.set(MappingNamespace.getNamespace(namespace))
    }

    /**
     * env type to remap against
     */
    fun setEnv(envType: String) {
        this.envType.set(EnvType.valueOf(envType))
    }

    init {
        devNamespace.convention(null as MappingNamespace?)
        devFallbackNamespace.convention(null as MappingNamespace?)
        prodNamespace.convention(null as MappingNamespace?)
        remapATToLegacy.convention(null as Boolean?)
        envType.convention(null as EnvType?)
    }

}