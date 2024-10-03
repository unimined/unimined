package xyz.wagyourtail.unimined.api.source.remapper

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.process.JavaExecSpec
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import java.nio.file.Path

/**
 * @since 1.2.0
 */
interface SourceRemapper {

    /**
     * set the remapper to use (defaults to https://github.com/unimined/source-remap)
     * @since 1.2.0
     */
    fun remapper(dep: Any) {
        remapper(dep) {}
    }

    /**
     * set the remapper to use (defaults to https://github.com/unimined/source-remap)
     * @since 1.2.0
     */
    fun remapper(dep: Any, action: Dependency.() -> Unit)

    fun remapper(
        dep: Any,
        @DelegatesTo(value = Dependency::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        remapper(dep) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Internal
    fun remap(
        inputOutput: Map<Path, Path>,
        classpath: FileCollection,
        source: MappingNamespaceTree.Namespace,
        sourceFallback: MappingNamespaceTree.Namespace,
        targetFallback: MappingNamespaceTree.Namespace,
        target: MappingNamespaceTree.Namespace,
        specConfig: JavaExecSpec.() -> Unit = {}
    )
}