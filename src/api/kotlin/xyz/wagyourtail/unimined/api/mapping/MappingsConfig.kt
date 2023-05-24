package xyz.wagyourtail.unimined.api.mapping

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig

/**
 * @since 1.0.0
 */
abstract class MappingsConfig(val project: Project, val minecraft: MinecraftConfig) {

    @set:ApiStatus.Internal
    @get:ApiStatus.Internal
    abstract var devNamespace: MappingNamespace

    @set:ApiStatus.Internal
    @get:ApiStatus.Internal
    abstract var devFallbackNamespace: MappingNamespace

    @get:ApiStatus.Internal
    abstract val mappingsDeps: MutableList<MappingDepConfig<*>>

    abstract var side: EnvType

    abstract val hasStubs: Boolean

    fun devNamespace(namespace: String) {
        devNamespace = MappingNamespace.getNamespace(namespace)
    }

    fun devFallbackNamespace(namespace: String) {
        devFallbackNamespace = MappingNamespace.getNamespace(namespace)
    }

    fun intermediary() {
        intermediary {}
    }

    abstract fun intermediary(action: MappingDepConfig<*>.() -> Unit)

    fun intermediary(
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        intermediary {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun legacyIntermediary() {
        legacyIntermediary(1)
    }

    fun legacyIntermediary(revision: Int) {
        legacyIntermediary(revision) {}
    }

    abstract fun legacyIntermediary(revision: Int, action: MappingDepConfig<*>.() -> Unit)

    fun legacyIntermediary(
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyIntermediary(1, action)
    }

    fun legacyIntermediary(
        revision: Int,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyIntermediary(revision) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun babricIntermediary() {
        babricIntermediary {}
    }

    abstract fun babricIntermediary(action: MappingDepConfig<*>.() -> Unit)

    fun babricIntermediary(
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        babricIntermediary {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun searge() {
        searge {}
    }

    fun searge(action: MappingDepConfig<*>.() -> Unit) {
        searge(minecraft.version, action)
    }

    abstract fun searge(version: String, action: MappingDepConfig<*>.() -> Unit)

    fun searge(
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        searge {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun hashed() {
        hashed {}
    }

    abstract fun hashed(action: MappingDepConfig<*>.() -> Unit)

    fun hashed(
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        hashed {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun mojmap() {
        mojmap {}
    }

    abstract fun mojmap(action: MappingDepConfig<*>.() -> Unit)

    fun mojmap(
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mojmap {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun mcp(channel: String, version: String) {
        mcp(channel, version) {}
    }

    abstract fun mcp(channel: String, version: String, action: MappingDepConfig<*>.() -> Unit)

    fun mcp(
        channel: String,
        version: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mcp(channel, version) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun yarn(build: Int) {
        yarn(build) {}
    }

    abstract fun yarn(build: Int, action: MappingDepConfig<*>.() -> Unit)

    fun yarn(
        build: Int,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        yarn(build) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun legacyYarn(build: Int) {
        legacyYarn(build, 1)
    }

    fun legacyYarn(build: Int, revision: Int) {
        legacyYarn(build, revision) {}
    }

    abstract fun legacyYarn(build: Int, revision: Int, action: MappingDepConfig<*>.() -> Unit)

    fun legacyYarn(
        build: Int,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build, 1, action)
    }

    fun legacyYarn(
        build: Int,
        revision: Int,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build, revision) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun barn(build: Int) {
        barn(build) {}
    }

    abstract fun barn(build: Int, action: MappingDepConfig<*>.() -> Unit)

    fun barn(
        build: Int,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        barn(build) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun quilt(build: Int) {
        quilt(build) {}
    }

    fun quilt(build: Int, action: MappingDepConfig<*>.() -> Unit) {
        quilt(build, "intermediary-v2", action)
    }

    abstract fun quilt(build: Int, classifier: String, action: MappingDepConfig<*>.() -> Unit)

    fun quilt(
        build: Int,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        quilt(build) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun mapping(
        dependency: Any,
    ) {
        mapping(dependency) {}
    }

    abstract fun mapping(
        dependency: Any,
        action: MappingDepConfig<*>.() -> Unit
    )

    fun mapping(
        dependency: Any,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mapping(dependency) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Experimental
    fun newNamespace(namespace: String, type: String) = MappingNamespace(namespace, MappingNamespace.Type.fromId(type))

    @ApiStatus.Internal
    abstract fun getTRMappings(
        remap: Pair<MappingNamespace, MappingNamespace>,
        remapLocals: Boolean = false
    ): (IMappingProvider.MappingAcceptor) -> Unit

    @get:ApiStatus.Internal
    abstract val available: Set<MappingNamespace>

    @get:ApiStatus.Internal
    abstract val combinedNames: String
}
