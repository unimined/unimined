package xyz.wagyourtail.unimined.api.mappings

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig

/**
 * @since 1.0.0
 */
abstract class MappingsConfig(val project: Project, val minecraft: MinecraftConfig) {
    abstract val devNamespace: MappingNamespace
    abstract val devFallbackNamespace: MappingNamespace

    abstract val mappingsDeps: MutableMap<Dependency, MappingConfig>

    abstract fun mapping(
        dependency: Any,
    )

    abstract fun mapping(
        dependency: Any,
        action: MappingConfig.() -> Unit
    )

    abstract fun mapping(
        dependency: Any,
        namespace: MappingNamespace,
        action: MappingConfig.() -> Unit
    )

    fun mapping(
        dependency: Any,
        @DelegatesTo(value = MappingConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mapping(dependency) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun mapping(
        dependency: Any,
        namespace: MappingNamespace,
        @DelegatesTo(value = MappingConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mapping(dependency, namespace) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun newNamespace(namespace: String, type: String) = MappingNamespace(namespace, MappingNamespace.Type.fromId(type))

}
