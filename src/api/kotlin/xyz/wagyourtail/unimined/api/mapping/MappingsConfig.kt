package xyz.wagyourtail.unimined.api.mapping

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.Project
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.util.FinalizeOnRead

/**
 * @since 1.0.0
 */
abstract class MappingsConfig(val project: Project, val minecraft: MinecraftConfig) : MappingNamespaceTree() {

    @set:ApiStatus.Internal
    @get:ApiStatus.Internal
    abstract var devNamespace: Namespace

    @set:ApiStatus.Internal
    @get:ApiStatus.Internal
    abstract var devFallbackNamespace: Namespace

    @get:ApiStatus.Internal
    abstract val mappingsDeps: MutableList<MappingDepConfig>

    abstract var side: EnvType

    abstract val hasStubs: Boolean

    protected val legacyFabricMappingsVersionFinalize = FinalizeOnRead(1)
    var legacyFabricMappingsVersion by legacyFabricMappingsVersionFinalize

    fun devNamespace(namespace: String) {
        devNamespace = getNamespace(namespace)
    }

    fun devFallbackNamespace(namespace: String) {
        devFallbackNamespace = getNamespace(namespace)
    }

    fun intermediary() {
        intermediary {}
    }

    abstract fun intermediary(action: MappingDepConfig.() -> Unit)

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

    fun legacyIntermediary(revision: String) {
        legacyIntermediary(revision.toInt())
    }

    abstract fun legacyIntermediary(revision: Int, action: MappingDepConfig.() -> Unit)

    fun legacyIntermediary(revision: String, action: MappingDepConfig.() -> Unit) {
        legacyIntermediary(revision.toInt(), action)
    }

    fun legacyIntermediary(
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyIntermediary(legacyFabricMappingsVersion, action)
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

    fun legacyIntermediary(
        revision: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyIntermediary(revision.toInt(), action)
    }

    fun babricIntermediary() {
        babricIntermediary {}
    }

    abstract fun babricIntermediary(action: MappingDepConfig.() -> Unit)

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

    fun officialMappingsFromJar() {
        officialMappingsFromJar {
            outputs("official", false) { listOf("official") }
        }
    }

    abstract fun officialMappingsFromJar(action: MappingDepConfig.() -> Unit)

    fun officialMappingsFromJar(
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        officialMappingsFromJar {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun searge() {
        searge(minecraft.version)
    }

    fun searge(action: MappingDepConfig.() -> Unit) {
        searge(minecraft.version, action)
    }

    fun searge(version: String) {
        officialMappingsFromJar()
        searge(version) {}
    }

    abstract fun searge(version: String, action: MappingDepConfig.() -> Unit)

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

    fun searge(
        version: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        searge(version) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun hashed() {
        hashed {}
    }

    abstract fun hashed(action: MappingDepConfig.() -> Unit)

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

    abstract fun mojmap(action: MappingDepConfig.() -> Unit)

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

    abstract fun mcp(channel: String, version: String, action: MappingDepConfig.() -> Unit)

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

    fun retroMCP() {
        retroMCP {}
    }

    abstract fun retroMCP(action: MappingDepConfig.() -> Unit)

    fun retroMCP(
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        retroMCP {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun yarn(build: Int) {
        yarn(build) {
        }
    }

    fun yarn(build: String) {
        yarn(build.toInt())
    }

    abstract fun yarn(build: Int, action: MappingDepConfig.() -> Unit)

    fun yarn(build: String, action: MappingDepConfig.() -> Unit) {
        yarn(build.toInt(), action)
    }

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

    fun yarn(
        build: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        yarn(build.toInt(), action)
    }

    fun legacyYarn(build: Int) {
        legacyYarn(build, legacyFabricMappingsVersion)
    }

    fun legacyYarn(build: String) {
        legacyYarn(build.toInt())
    }

    fun legacyYarn(build: Int, revision: Int) {
        legacyYarn(build, revision) {
        }
    }

    fun legacyYarn(build: String, revision: String) {
        legacyYarn(build.toInt(), revision.toInt())
    }

    fun legacyYarn(build: String, revision: Int) {
        legacyYarn(build.toInt(), revision)
    }

    fun legacyYarn(build: Int, revision: String) {
        legacyYarn(build, revision.toInt())
    }



    abstract fun legacyYarn(build: Int, revision: Int, action: MappingDepConfig.() -> Unit)

    fun legacyYarn(build: String, revision: String, action: MappingDepConfig.() -> Unit) {
        legacyYarn(build.toInt(), revision.toInt(), action)
    }

    fun legacyYarn(build: String, revision: Int, action: MappingDepConfig.() -> Unit) {
        legacyYarn(build.toInt(), revision, action)
    }

    fun legacyYarn(build: Int, revision: String, action: MappingDepConfig.() -> Unit) {
        legacyYarn(build, revision.toInt(), action)
    }

    fun legacyYarn(
        build: Int,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build, legacyFabricMappingsVersion, action)
    }

    fun legacyYarn(
        build: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build.toInt(), action)
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

    fun legacyYarn(
        build: String,
        revision: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build.toInt(), revision.toInt(), action)
    }

    fun legacyYarn(
        build: String,
        revision: Int,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build.toInt(), revision, action)
    }

    fun legacyYarn(
        build: Int,
        revision: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build, revision.toInt(), action)
    }

    fun barn(build: Int) {
        barn(build) {
        }
    }

    fun barn(build: String) {
        barn(build.toInt())
    }

    abstract fun barn(build: Int, action: MappingDepConfig.() -> Unit)

    fun barn(build: String, action: MappingDepConfig.() -> Unit) {
        barn(build.toInt(), action)
    }

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

    fun barn(
        build: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        barn(build.toInt(), action)
    }

    fun quilt(build: Int) {
        quilt(build, "intermediary-v2")
    }

    fun quilt(build: String) {
        quilt(build.toInt())
    }

    fun quilt(build: Int, action: MappingDepConfig.() -> Unit) {
        quilt(build, "intermediary-v2", action)
    }

    fun quilt(build: String, action: MappingDepConfig.() -> Unit) {
        quilt(build.toInt(), action)
    }

    fun quilt(build: Int, classifier: String) {
        quilt(build, classifier) {
        }
    }

    fun quilt(build: String, classifier: String) {
        quilt(build.toInt(), classifier)
    }

    abstract fun quilt(build: Int, classifier: String, action: MappingDepConfig.() -> Unit)

    fun quilt(build: String, classifier: String, action: MappingDepConfig.() -> Unit) {
        quilt(build.toInt(), classifier, action)
    }

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

    fun quilt(
        build: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        quilt(build.toInt(), action)
    }

    fun forgeBuiltinMCP(version: String) {
        forgeBuiltinMCP(version) {
        }
    }



    abstract fun forgeBuiltinMCP(version: String, action: MappingDepConfig.() -> Unit)

    fun forgeBuiltinMCP(
        version: String,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        forgeBuiltinMCP(version) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    abstract fun mapping(
        dependency: Any,
        action: MappingDepConfig.() -> Unit
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

    @ApiStatus.Internal
    abstract fun getTRMappings(
        remap: Pair<Namespace, Namespace>,
        remapLocals: Boolean = false
    ): (IMappingProvider.MappingAcceptor) -> Unit

    @get:ApiStatus.Internal
    abstract val combinedNames: String
    abstract val stub: MemoryMapping
}
