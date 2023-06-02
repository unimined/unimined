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
    abstract val mappingsDeps: MutableList<MappingDepConfig<*>>

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
        intermediary {
            outputs("intermediary", false) { listOf("official") }
        }
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
        legacyIntermediary(revision) {
            outputs("intermediary", false) { listOf("official") }
        }
    }

    abstract fun legacyIntermediary(revision: Int, action: MappingDepConfig<*>.() -> Unit)

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

    fun babricIntermediary() {
        babricIntermediary {
            if (side == EnvType.COMBINED) throw IllegalStateException("Cannot use babricIntermediary with side COMBINED")
            mapNamespace(side.classifier!!, "official")
            outputs("intermediary", false) { listOf("official") }
        }
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
        searge(minecraft.version)
    }

    fun searge(action: MappingDepConfig<*>.() -> Unit) {
        searge(minecraft.version, action)
    }

    fun searge(version: String) {
        searge(version)  {
            mapNamespace("obf", "official")
            outputs("searge", false) { listOf("official") }
        }
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
        hashed {
            outputs("hashed", false) { listOf("official") }
        }
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
        mojmap {
            outputs("mojmap", true) {
                // check if we have searge or intermediary or hashed mappings
                val searge = if ("searge" in getNamespaces()) listOf("searge") else emptyList()
                val intermediary = if ("intermediary" in getNamespaces()) listOf("intermediary") else emptyList()
                val hashed = if ("hashed" in getNamespaces()) listOf("hashed") else emptyList()
                listOf("official") + intermediary + searge + hashed
            }
        }
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
        mcp(channel, version) {
            if (channel == "legacy") {
                outputs("searge", false) { listOf("official") }
                sourceNamespace {
                    if (it.contains("MCP")) {
                        "searge"
                    } else {
                        "official"
                    }
                }
            } else {
                sourceNamespace("searge")
            }
            outputs("mcp", true) { listOf("searge") }
        }
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

    fun retroMCP() {
        retroMCP {
            if (minecraft.minecraftData.mcVersionCompare(minecraft.version, "1.3") < 0) {
                if (side == EnvType.COMBINED) throw IllegalStateException("Cannot use retroMCP with side COMBINED")
                mapNamespace(side.classifier!!, "official")
            }
            mapNamespace("named", "mcp")
            outputs("mcp", true) { listOf("official") }
        }
    }

    abstract fun retroMCP(action: MappingDepConfig<*>.() -> Unit)

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
            outputs("yarn", true) { listOf("intermediary") }
            sourceNamespace("intermediary")
        }
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
        legacyYarn(build, legacyFabricMappingsVersion)
    }

    fun legacyYarn(build: Int, revision: Int) {
        legacyYarn(build, revision) {
            outputs("yarn", true) { listOf("intermediary") }
            sourceNamespace("intermediary")
        }
    }

    abstract fun legacyYarn(build: Int, revision: Int, action: MappingDepConfig<*>.() -> Unit)

    fun legacyYarn(
        build: Int,
        @DelegatesTo(value = MappingDepConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        legacyYarn(build, legacyFabricMappingsVersion, action)
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
        barn(build) {
            outputs("barn", true) { listOf("intermediary") }
            sourceNamespace("intermediary")
        }
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
        quilt(build, "intermediary-v2")
    }

    fun quilt(build: Int, action: MappingDepConfig<*>.() -> Unit) {
        quilt(build, "intermediary-v2", action)
    }

    fun quilt(build: Int, classifier: String) {
        quilt(build, classifier) {
            mapNamespace("named", "quilt")
            val intermediary = if (classifier.contains("intermediary")) listOf("intermediary") else emptyList()
            val hashed = if (intermediary.isEmpty()) listOf("hashed") else emptyList()
            outputs("quilt", true) {
                intermediary + hashed
            }
            sourceNamespace((intermediary + hashed).first())
        }
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

    fun forgeBuiltinMCP(version: String) {
        forgeBuiltinMCP(version) {
            outputs("searge", false) { listOf("official") }
            outputs("mcp", true) { listOf("searge") }
            sourceNamespace {
                if (it == "MCP") {
                    "searge"
                } else {
                    "official"
                }
            }
        }
    }

    abstract fun forgeBuiltinMCP(version: String, action: MappingDepConfig<*>.() -> Unit)

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

    @ApiStatus.Internal
    abstract fun getTRMappings(
        remap: Pair<Namespace, Namespace>,
        remapLocals: Boolean = false
    ): (IMappingProvider.MappingAcceptor) -> Unit

    @get:ApiStatus.Internal
    abstract val combinedNames: String
    abstract val stub: MemoryMapping
}
