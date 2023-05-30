package xyz.wagyourtail.unimined.api.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree

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
    abstract val devNamespace: Property<MappingNamespaceTree.Namespace?>

    /**
     * the dev env fallback mappings
     * defaults to {@link mcConfig.mappings.devNamespace}
     */
    @get:Input
    @get:Optional
    @get:ApiStatus.Internal
    abstract val devFallbackNamespace: Property<MappingNamespaceTree.Namespace?>

    /**
     * the prod env mappings
     * defaults to {@link mcConfig.mcProvider.prodNamespace}
     */
    @get:Input
    @get:Optional
    @get:ApiStatus.Internal
    abstract val prodNamespace: Property<MappingNamespaceTree.Namespace?>

    /**
     * whether to remap AccessTransformers to the legacy format (<=1.7.10)
     */
    @get:Input
    @get:Optional
    abstract val remapATToLegacy: Property<Boolean?>

    abstract fun devNamespace(namespace: String)

    abstract fun devFallbackNamespace(namespace: String)

    abstract fun prodNamespace(namespace: String)

    init {
        devNamespace.convention(null as MappingNamespaceTree.Namespace?)
        devFallbackNamespace.convention(null as MappingNamespaceTree.Namespace?)
        prodNamespace.convention(null as MappingNamespaceTree.Namespace?)
        remapATToLegacy.convention(null as Boolean?)
    }

}