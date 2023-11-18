package xyz.wagyourtail.unimined.api.mapping

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.util.FinalizeOnRead

/**
 * @since 1.0.0
 */
@Suppress("OVERLOADS_ABSTRACT", "UNUSED")
abstract class MappingsConfig(val project: Project, val minecraft: MinecraftConfig) : MappingNamespaceTree() {

    @set:ApiStatus.Internal
    @get:ApiStatus.Internal
    abstract var devNamespace: Namespace

    @set:ApiStatus.Internal
    @get:ApiStatus.Internal
    abstract var devFallbackNamespace: Namespace

    @get:ApiStatus.Internal
    abstract val mappingsDeps: MutableMap<String, MappingDepConfig>

    abstract var side: EnvType

    abstract val hasStubs: Boolean

    protected val legacyFabricMappingsVersionFinalize = FinalizeOnRead(1)
    var legacyFabricMappingsVersion by legacyFabricMappingsVersionFinalize

    abstract fun devNamespace(namespace: String)

    abstract fun devFallbackNamespace(namespace: String)

    fun isEmpty(): Boolean {
        return mappingsDeps.isEmpty()
    }

    @ApiStatus.Experimental
    abstract fun removeKey(key: String)

    @JvmOverloads
    abstract fun intermediary(key: String = "intermediary", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun intermediary(
        key: String = "intermediary",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        intermediary(key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    abstract fun legacyIntermediary(revision: Int = 1, key: String = "intermediary", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun legacyIntermediary(revision: String, key: String = "intermediary", action: MappingDepConfig.() -> Unit = {}) {
        legacyIntermediary(revision.toInt(), key, action)
    }

    @JvmOverloads
    fun legacyIntermediary(
        revision: Int = 1,
        key: String = "intermediary",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyIntermediary(revision, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    fun legacyIntermediary(
        revision: String,
        key: String = "intermediary",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyIntermediary(revision.toInt(), key, action)
    }

    @JvmOverloads
    abstract fun babricIntermediary(key: String = "intermediary", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun babricIntermediary(
        key: String = "intermediary",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        babricIntermediary(key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    abstract fun officialMappingsFromJar(key: String = "official", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun officialMappingsFromJar(
        key: String = "official",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        officialMappingsFromJar(key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }


    @JvmOverloads
    abstract fun searge(version: String = minecraft.version, key: String = "searge", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun searge(
        version: String = minecraft.version,
        key: String = "searge",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        searge(version, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    abstract fun hashed(key: String = "hashed", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun hashed(
        key: String = "hashed",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        hashed(key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    abstract fun mojmap(key: String = "mojmap", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun mojmap(
        key: String = "mojmap",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mojmap(key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads

    abstract fun mcp(channel: String, version: String, key: String = "mcp", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun mcp(
        channel: String,
        version: String,
        key: String = "mcp",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mcp(channel, version, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    abstract fun retroMCP(version: String = minecraft.version, key: String = "mcp", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun retroMCP(
        version: String = minecraft.version,
        key: String = "mcp",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        retroMCP(version, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    abstract fun yarn(build: Int, key: String = "yarn", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun yarn(build: String, key: String = "yarn", action: MappingDepConfig.() -> Unit = {}) {
        yarn(build.toInt(), key, action)
    }

    @JvmOverloads
    fun yarn(
        build: Int,
        key: String = "yarn",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        yarn(build, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    fun yarn(
        build: String,
        key: String = "yarn",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        yarn(build.toInt(), key, action)
    }

    @JvmOverloads
    abstract fun legacyYarn(build: Int, revision: Int = 1, key: String = "yarn", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun legacyYarn(build: String, revision: Int = 1, key: String = "yarn", action: MappingDepConfig.() -> Unit = {}) {
        legacyYarn(build.toInt(), revision, key, action)
    }

    @JvmOverloads
    fun legacyYarn(build: Int, revision: String, key: String = "yarn", action: MappingDepConfig.() -> Unit = {}) {
        legacyYarn(build, revision.toInt(), key, action)
    }

    @JvmOverloads
    fun legacyYarn(build: String, revision: String, key: String = "yarn", action: MappingDepConfig.() -> Unit = {}) {
        legacyYarn(build.toInt(), revision.toInt(), key, action)
    }

    @JvmOverloads
    fun legacyYarn(
        build: Int,
        revision: Int = 1,
        key: String = "yarn",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build, revision, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    fun legacyYarn(
        build: String,
        revision: Int = 1,
        key: String = "yarn",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build.toInt(), revision, key, action)
    }

    @JvmOverloads
    fun legacyYarn(
        build: Int,
        revision: String,
        key: String = "yarn",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build, revision.toInt(), key, action)
    }

    @JvmOverloads
    fun legacyYarn(
        build: String,
        revision: String,
        key: String = "yarn",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build.toInt(), revision.toInt(), key, action)
    }

    @JvmOverloads
    abstract fun barn(build: Int, key: String = "yarn", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun barn(build: String, key: String = "yarn", action: MappingDepConfig.() -> Unit = {}) {
        barn(build.toInt(), key, action)
    }

    @JvmOverloads
    fun barn(
        build: Int,
        key: String = "yarn",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        barn(build, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    fun barn(
        build: String,
        key: String = "yarn",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        barn(build.toInt(), key, action)
    }

    @JvmOverloads
    abstract fun biny(commitName: String, key: String = "yarn", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun biny(
        commitName: String,
        key: String = "yarn",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        biny(commitName, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }


    @JvmOverloads
    abstract fun quilt(build: Int, classifier: String = "intermediary-v2", key: String = "quilt", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun quilt(build: String, classifier: String = "intermediary-v2", key: String = "quilt", action: MappingDepConfig.() -> Unit = {}) {
        quilt(build.toInt(), classifier, key, action)
    }

    @JvmOverloads
    fun quilt(
        build: Int,
        classifier: String = "intermediary-v2",
        key: String = "quilt",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        quilt(build, classifier, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    fun quilt(
        build: String,
        classifier: String = "intermediary-v2",
        key: String = "quilt",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        quilt(build.toInt(), classifier, key, action)
    }

    @JvmOverloads
    abstract fun forgeBuiltinMCP(version: String, key: String = "mcp", action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    fun forgeBuiltinMCP(
        version: String,
        key: String = "mcp",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        forgeBuiltinMCP(version, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @JvmOverloads
    abstract fun parchment(
        mcVersion: String = minecraft.version,
        version: String,
        checked: Boolean = false,
        key: String = "parchment",
        action: MappingDepConfig.() -> Unit = {}
    )

    @JvmOverloads
    fun parchment(
        mcVersion: String = minecraft.version,
        version: String,
        checked: Boolean = false,
        key: String = "parchment",
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        parchment(mcVersion, version, checked, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Experimental
    abstract fun postProcess(key: String, mappings: MappingsConfig.() -> Unit, merger: MappingDepConfig.() -> Unit)

    @ApiStatus.Experimental
    fun postProcess(
        key: String,
        @DelegatesTo(value = MappingsConfig::class, strategy = Closure.DELEGATE_FIRST)
        mappings: Closure<*>,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        merger: Closure<*>
    ) {
        postProcess(key, {
            mappings.delegate = this
            mappings.resolveStrategy = Closure.DELEGATE_FIRST
            mappings.call()
        }) {
            merger.delegate = this
            merger.resolveStrategy = Closure.DELEGATE_FIRST
            merger.call()
        }
    }

    @JvmOverloads
    @ApiStatus.Experimental
    abstract fun mapping(dependency: Any, key: String = if (dependency is Dependency) dependency.name else dependency.toString(), action: MappingDepConfig.() -> Unit = {})

    @JvmOverloads
    @ApiStatus.Experimental
    fun mapping(
        dependency: Any,
        key: String = dependency.toString(),
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mapping(dependency, key) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Internal
    abstract fun getTRMappings(
        remap: Pair<Namespace, Namespace>,
        remapLocals: Boolean = false
    ): (IMappingProvider.MappingAcceptor) -> Unit

    @get:ApiStatus.Internal
    abstract val combinedNames: String
    abstract val stub: MemoryMapping
}
