package xyz.wagyourtail.unimined.internal.mapping

import net.fabricmc.mappingio.format.Tiny2Writer2
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.api.mappings.MemoryMapping
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.toHex
import java.io.StringWriter
import java.nio.file.Path
import java.security.MessageDigest

class MappingsProvider(project: Project, minecraft: MinecraftConfig): MappingsConfig(project, minecraft) {

    override var side: EnvType
        get() = minecraft.side
        set(value) {
            minecraft.side = value
        }

    private var freeze = false

    override var devNamespace by FinalizeOnRead(LazyMutable {
        available.first { it.type == MappingNamespace.Type.NAMED }
    })

    override var devFallbackNamespace by FinalizeOnRead(LazyMutable {
        available.first { it.type == MappingNamespace.Type.INT }
    })

    override val mappingsDeps = mutableListOf<MappingDepConfig<*>>()

    private val available: Set<MappingNamespace>
        get() {
        TODO("Not yet implemented")
    }

    override fun mapping(dependency: Any, action: MappingDepConfig<*>.() -> Unit) {
        if (mappingTreeLazy.isInitialized()) {
            throw IllegalStateException("Cannot add mappings after mapping tree has been initialized")
        }
        project.dependencies.create(dependency).also {
            if (it is ExternalModuleDependency) {
                ExternalMappingDepImpl(it).action()
            } else {
                MappingDepImpl(it).action()
            }
        }
    }

    private var _stub: MemoryMapping? = null

    val stub: MemoryMapping
        get() {
            if (freeze) {
                throw IllegalStateException("Cannot access stub after mapping tree has been initialized")
            }
            if (_stub == null) {
                _stub = MemoryMapping()
            }
            return _stub!!
        }

    override val hasStubs: Boolean
        get() =_stub != null

    private val mappingTreeLazy = lazy {
        project.logger.lifecycle("Resolving mappings for ${minecraft.sourceSet}")
        val mappings = MemoryMappingTree()
        freeze = true
        TODO()
        mappings
    }

    fun mappingCacheFile(): Path =
        (if (_stub != null) project.unimined.getLocalCache() else project.unimined.getGlobalCache())
            .resolve("mappings-${side}-${combinedNames}.jar")

    val mappingTree: MappingTreeView
        get() = mappingTreeLazy.value

    val combinedNames: String by lazy {
        freeze = true
        val names = mappingsDeps.map { "${it.name}-${it.version}" }.sorted() + (if (hasStubs) listOf("stub-${_stub!!.hash}") else listOf())
        names.joinToString("-")
    }

    fun MappingTreeView.sha256() {
        val sha = MessageDigest.getInstance("SHA-256")
        val stringWriter = StringWriter()
        this.accept(Tiny2Writer2(stringWriter, false))
        sha.update(stringWriter.toString().toByteArray())
        sha.digest().toHex().substring(0..8)
    }
}