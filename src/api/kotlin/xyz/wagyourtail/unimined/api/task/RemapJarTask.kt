package xyz.wagyourtail.unimined.api.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.util.FinalizeOnRead

/**
 * task responsible for transforming your built jar to production.
 * @since 0.1.0
 */
@Suppress("LeakingThis")
abstract class RemapJarTask : Jar() {

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Internal
    @set:Internal
    var devNamespace: MappingNamespaceTree.Namespace? by FinalizeOnRead(null)

    @get:Internal
    @set:Internal
    var devFallbackNamespace: MappingNamespaceTree.Namespace? by FinalizeOnRead(null)

    @get:Internal
    @set:Internal
    var prodNamespace: MappingNamespaceTree.Namespace? by FinalizeOnRead(null)

    /**
     * whether to remap AccessTransformers to the legacy format (<=1.7.10)
     */
    @get:Input
    @get:Optional
    abstract val remapATToLegacy: Property<Boolean?>

    @get:Internal
    @set:Internal
    @set:ApiStatus.Experimental
    abstract var allowImplicitWildcards: Boolean

    abstract fun devNamespace(namespace: String)

    abstract fun devFallbackNamespace(namespace: String)

    abstract fun prodNamespace(namespace: String)

    init {
        remapATToLegacy.convention(null as Boolean?)
    }

}