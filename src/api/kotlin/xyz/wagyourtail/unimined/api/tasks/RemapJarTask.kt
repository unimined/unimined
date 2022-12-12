package xyz.wagyourtail.unimined.api.tasks

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
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
    abstract val sourceNamespace: Property<String?>

    /**
     * the dev env fallback mappings
     */
    @get:Input
    @get:Optional
    abstract val fallbackFromNamespace: Property<String?>

    /**
     * the prod env fallback mappings
     */
    @get:Input
    @get:Optional
    abstract val fallbackTargetNamespace: Property<String?>

    /**
     * the prod env mappings
     */
    @get:Input
    @get:Optional
    abstract val targetNamespace: Property<String?>

    /**
     * remap through official, use on forge in multi project builds for best results.
     */
    @get:Input
    @get:Optional
    abstract val remapThroughOfficial: Property<Boolean>

    /**
     * whether to remap AccessTransformers to the legacy format (<=1.7.10)
     */
    @get:Input
    @get:Optional
    abstract val remapATToLegacy: Property<Boolean>

    /**
     * env type to remap against
     */
    @get:Input
    @get:Optional
    @get:ApiStatus.Internal
    abstract val envType: Property<EnvType>

    /**
     * env type to remap against
     */
    fun setEnv(envType: String) {
        this.envType.set(EnvType.valueOf(envType))
    }

    init {
        sourceNamespace.convention(null as String?)
        fallbackFromNamespace.convention(null as String?)
        fallbackTargetNamespace.convention(null as String?)
        targetNamespace.convention(null as String?)
        remapATToLegacy.convention(false)
        remapThroughOfficial.convention(false)
        envType.convention(EnvType.COMBINED)
    }
}