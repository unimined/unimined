package xyz.wagyourtail.unimined.api.minecraft.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree

interface RemapOptions {

    @get:InputFile
    val inputFile: RegularFileProperty

    @get:Internal
    @set:Internal
    var devNamespace: MappingNamespaceTree.Namespace?

    @get:Internal
    @set:Internal
    var devFallbackNamespace: MappingNamespaceTree.Namespace?

    @get:Internal
    @set:Internal
    var prodNamespace: MappingNamespaceTree.Namespace?

    @get:Internal
    @set:Internal
    @set:ApiStatus.Experimental
    var allowImplicitWildcards: Boolean

    fun devNamespace(namespace: String)

    fun devFallbackNamespace(namespace: String)

    fun prodNamespace(namespace: String)

}