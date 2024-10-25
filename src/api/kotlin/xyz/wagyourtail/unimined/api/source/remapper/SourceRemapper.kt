package xyz.wagyourtail.unimined.api.source.remapper

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.FileCollection
import org.gradle.process.JavaExecSpec
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.mapping.Namespace
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
    fun remapper(dep: Any, action: ExternalModuleDependency.() -> Unit)

    fun remapper(
        dep: Any,
        @DelegatesTo(value = ExternalModuleDependency::class, strategy = Closure.DELEGATE_FIRST)
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
        source: Namespace,
        target: Namespace,
        specConfig: JavaExecSpec.() -> Unit = {}
    )
}