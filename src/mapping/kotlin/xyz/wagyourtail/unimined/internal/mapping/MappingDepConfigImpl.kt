package xyz.wagyourtail.unimined.internal.mapping

import net.fabricmc.mappingio.format.*
import net.fabricmc.mappingio.tree.MappingTreeView
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
    val project = mappingsConfig.project
    val inputs = MappingTreeBuilder.MappingInputBuilder()
    private var finalized by FinalizeOnWrite(false)
    private val contained = mutableListOf<ContainedMappingImpl>()

    init {
        defaultContains.dep = this
        inputs.provides({ _, _ -> true }) {
            defaultContains.build(this)
        }
    }

    fun finalize() {
        if (!finalized) finalized = true
        defaultContains.finalize()
        contained.forEach(ContainedMappingImpl::finalize)
        project.logger.info("[Unimined/MappingDep] Finalized ($dep)")
    }

    fun checkFinalized() {
        if (finalized) throw IllegalStateException("Cannot modify finalized MappingDepConfig")
    }

    override fun contains(acceptor: (fname: String, type: String) -> Boolean, action: ContainedMapping.() -> Unit) {
        checkFinalized()
        val containedMapping = ContainedMappingImpl(this@MappingDepConfigImpl)
        action(containedMapping)
        inputs.provides({ f, t ->
            acceptor(f, t.toString())
        }) {
            containedMapping.build(this)
        }
        contained.add(containedMapping)
    }

    override fun clearContains() {
        checkFinalized()
        contained.clear()
        inputs.clearProvides()
        inputs.provides({ _, _ -> true }) {
            defaultContains.build(this)
        }
    }
}

class ContainedMappingImpl() : ContainedMapping {
    lateinit var dep: MappingDepConfigImpl

    val inputActions = mutableListOf<MappingTreeBuilder.MappingInputBuilder.MappingInput.() -> Unit>()
    private val outputs = mutableListOf<MappingDepConfig.TempMappingNamespace>()
    private var finalized by FinalizeOnWrite(false)
    val mappingsConfig by lazy { dep.mappingsConfig }

    constructor(dep: MappingDepConfigImpl) : this() {
        this.dep = dep
    }

    fun checkFinalized() {
        if (finalized) throw IllegalStateException("Cannot modify finalized MappingDepConfig")
    }

    fun finalize() {
        if (!finalized) {
            finalized = true
            outputs.forEach {
                mappingsConfig.project.logger.info("[Unimined/MappingDep] ${dep.dep} adding namespace ${it.actualNamespace.name}") // force resolve
            }
        }
    }

    fun build(input: MappingTreeBuilder.MappingInputBuilder.MappingInput) {
        finalize()
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

    override fun skipIfNotIn(namespace: String) {
        checkFinalized()
        inputActions.add {
            addDepends(namespace)
            forwardVisitor { v, m, _ ->
                SkipIfNotInFilter(v, m, namespace)
            }
        }
    }

    override fun clearForwardVisitor() {
        checkFinalized()
        inputActions.add {
            clearForwardVisitor()
        }
    }

    override fun clearDepends() {
        checkFinalized()
        inputActions.add {
            clearDepends()
        }
    }

    override fun copyUnnamedFromSrc() {
        checkFinalized()
        inputActions.add {
            afterRemap { mappings ->
                val srcKey = mappings.getNamespaceId(nsSource)

                if (srcKey == MappingTreeView.NULL_NAMESPACE_ID) {
                    throw IllegalStateException("Source namespace $nsSource does not exist")
                }

                val outputKeys = outputs.map { mappings.getNamespaceId(it.actualNamespace.name) }.filter { it >= 0 }

                for (clazz in mappings.classes) {
                    for (key in outputKeys) {
                        if (clazz.getDstName(key) == null) {
                            clazz.setDstName(clazz.getName(srcKey), key)
                        }
                    }
                    for (method in clazz.methods) {
                        for (key in outputKeys) {
                            if (method.getDstName(key) == null) {
                                method.setDstName(method.getName(srcKey), key)
                            }
                        }
                    }
                    for (field in clazz.fields) {
                        for (key in outputKeys) {
                            if (field.getDstName(key) == null) {
                                field.setDstName(field.getName(srcKey), key)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun clearAfterRead() {
        checkFinalized()
        inputActions.add {
            clearAfterRemap()
        }
    }

    companion object {
        private val namespaceCreationTrace: MutableMap<String, Exception> = mutableMapOf()
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
                outputs.add(it)
            }
        }
        val createdException = Exception("Namespace created here")
        return object : MappingDepConfig.TempMappingNamespace(namespace, named, canRemapTo) {
            override val actualNamespace by lazy {
                try {
                    mappingsConfig.addNamespace(
                        namespace,
                        { canRemapTo().map { mappingsConfig.getNamespace(it.lowercase()) }.toSet() },
                        named
                    ).also {
                        inputActions.add {
                            addNs(it.name)
                        }
                        namespaceCreationTrace[namespace.lowercase()] = createdException
                    }
                } catch (e: IllegalArgumentException) {
                    mappingsConfig.project.logger.error(
                        "[Unimined/MappingDep] ${dep.dep} failed to add namespace $namespace",
                        e
                    )
                    mappingsConfig.project.logger.error("[Unimined/MappingDep] namespace ${namespace} originally created here", namespaceCreationTrace[namespace.lowercase()])
                    mappingsConfig.project.logger.error("[Unimined/MappingDep] namespace ${namespace} created for a second time here", createdException)
                    throw e
                }
            }
        }.also {
            outputs.add(it)
        }
    }

    override fun clearOutputs() {
        checkFinalized()
        outputs.clear()
    }
}