package xyz.wagyourtail.unimined.internal.mapping

import net.fabricmc.mappingio.format.ChildMethodStripper
import net.fabricmc.mappingio.format.MappingTreeBuilder
import net.fabricmc.mappingio.format.NoNewSrcVisitor
import net.fabricmc.mappingio.format.SrgToSeargeMapper
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.mapping.ContainedMapping
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.util.FinalizeOnWrite

class MappingDepConfigImpl(dep: Dependency, mappingsConfig: MappingsConfig, val defaultContains: ContainedMappingImpl = ContainedMappingImpl()): MappingDepConfig(dep,
    mappingsConfig
), ContainedMapping by defaultContains {
    val inputs = MappingTreeBuilder.MappingInputBuilder()
    val outputs = mutableListOf<TempMappingNamespace>()
    private var finalized by FinalizeOnWrite(false)

    init {
        defaultContains.dep = this
        inputs.provides({ _, _ -> true }) {
            defaultContains.finalize(this)
        }
    }

    fun finalize() {
        if (finalized) return
        outputs.forEach {
            mappingsConfig.project.logger.info("[Unimined/MappingDep] $dep adding namespace ${it.actualNamespace.name}") // force resolve
        }

        finalized = true
    }

    fun checkFinalized() {
        if (finalized) throw IllegalStateException("Cannot modify finalized MappingDepConfig")
    }

    override fun contains(acceptor: (fname: String, type: String) -> Boolean, action: ContainedMapping.() -> Unit) {
        val containedMapping = ContainedMappingImpl(this@MappingDepConfigImpl)
        action(containedMapping)
        inputs.provides({ f, t ->
            acceptor(f, t.toString())
        }) {
            containedMapping.finalize(this)
        }
    }

    override fun clearContains() {
        checkFinalized()
        inputs.clearProvides()
        inputs.provides({ _, _ -> true }) {
            defaultContains.finalize(this)
        }
    }
}

class ContainedMappingImpl() : ContainedMapping {
    lateinit var dep: MappingDepConfigImpl

    val inputActions = mutableListOf<MappingTreeBuilder.MappingInputBuilder.MappingInput.() -> Unit>()
    var finalized by FinalizeOnWrite(false)
    val mappingsConfig by lazy { dep.mappingsConfig }

    constructor(dep: MappingDepConfigImpl) : this() {
        this.dep = dep
    }

    fun checkFinalized() {
        if (finalized) throw IllegalStateException("Cannot modify finalized MappingDepConfig")
    }

    fun finalize(input: MappingTreeBuilder.MappingInputBuilder.MappingInput) {
        if (!finalized) finalized = true
        inputActions.forEach { input.it() }
    }

    override fun mapNamespace(from: String, to: String) {
        checkFinalized()
        inputActions.add { mapNs(from, to) }
    }

    override fun sourceNamespace(namespace: String) {
        checkFinalized()
        inputActions.add { setSource(namespace) }
    }

    override fun srgToSearge() {
        checkFinalized()
        inputActions.add {
            forwardVisitor { v, _ ->
                SrgToSeargeMapper(
                    v,
                    "srg",
                    "searge",
                    "mojmap",
                    (mappingsConfig as MappingsProvider)::getMojmapMappings
                )
            }
        }
    }

    override fun onlyExistingSrc() {
        checkFinalized()
        inputActions.add {
            forwardVisitor { v, m ->
                NoNewSrcVisitor(v, m)
            }
        }
    }

    override fun childMethodStrip() {
        checkFinalized()
        inputActions.add {
            forwardVisitor { v, _, e ->
                val mcFile = when (e) {
                    EnvType.CLIENT -> mappingsConfig.minecraft.minecraftData.minecraftClientFile
                    EnvType.COMBINED -> mappingsConfig.minecraft.mergedOfficialMinecraftFile
                    EnvType.SERVER, EnvType.DATAGEN -> mappingsConfig.minecraft.minecraftData.minecraftServerFile
                }
                ChildMethodStripper(v, mcFile.toPath())
            }
        }
    }

    override fun clearForwardVisitor() {
        checkFinalized()
        inputActions.add {
            clearForwardVisitor()
        }
    }

    override fun outputs(namespace: String, named: Boolean, canRemapTo: () -> List<String>): MappingDepConfig.TempMappingNamespace {
        checkFinalized()
        if (namespace.lowercase() == mappingsConfig.OFFICIAL.name) {
            return object : MappingDepConfig.TempMappingNamespace(namespace, named, canRemapTo) {
                override val actualNamespace: MappingNamespaceTree.Namespace
                    get() = mappingsConfig.OFFICIAL.also {
                        inputActions.add {
                            addNs(it.name)
                        }
                    }
            }.also {
                dep.outputs.add(it)
            }
        }
        return object : MappingDepConfig.TempMappingNamespace(namespace, named, canRemapTo) {
            override val actualNamespace by lazy {
                mappingsConfig.addNamespace(namespace, { canRemapTo().map { mappingsConfig.getNamespace(it.lowercase()) }.toSet() }, named).also {
                    inputActions.add {
                        addNs(it.name)
                    }
                }
            }
        }.also {
            dep.outputs.add(it)
        }
    }

    override fun clearOutputs() {
        checkFinalized()
        dep.outputs.clear()
    }
}