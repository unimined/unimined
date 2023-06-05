package xyz.wagyourtail.unimined.internal.mapping

import net.fabricmc.mappingio.format.MappingTreeBuilder
import net.fabricmc.mappingio.format.NoNewSrcVisitor
import net.fabricmc.mappingio.format.SrgToSeargeMapper
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.util.FinalizeOnWrite

class MappingDepConfigImpl(dep: Dependency, mappingsConfig: MappingsConfig): MappingDepConfig(dep,
    mappingsConfig
) {
    val inputs = MappingTreeBuilder.MappingInputBuilder()
    val outputs = mutableListOf<MappingDepConfig.TempMappingNamespace>()
    var finalized by FinalizeOnWrite(false)

    fun finalize() {
        if (finalized) return
        outputs.forEach {
            mappingsConfig.project.logger.info("[Unimined/MappingDep] $dep adding namespace ${it.actualNamespace.name}") // force resolve
        }
        finalized = true

        // TODO: other checks
        if (inputs.nsFilter.isEmpty()) {
            throw IllegalStateException("No namespaces specified for mapping dependency $dep")
        }
    }

    fun checkFinalized() {
        if (finalized) throw IllegalStateException("Cannot modify finalized MappingDepConfig")
    }

    override fun mapNamespace(from: String, to: String) {
        checkFinalized()
        inputs.mapNs(from, to)
    }

    override fun sourceNamespace(namespace: String) {
        checkFinalized()
        inputs.setSource(namespace)
    }

    override fun sourceNamespace(mappingTypeToSrc: (String) -> String) {
        checkFinalized()
        inputs.setSource {
            mappingTypeToSrc(it.name)
        }
    }

    override fun srgToSearge() {
        checkFinalized()
        inputs.forwardVisitor { v, m ->
            SrgToSeargeMapper(v, "srg", "searge", "mojmap", (mappingsConfig as MappingsProvider)::getMojmapMappings)
        }
    }

    override fun onlyExistingSrc() {
        checkFinalized()
        inputs.forwardVisitor { v, m ->
            NoNewSrcVisitor(v, m)
        }
    }

    override fun childMethodStrip() {
        checkFinalized()
        inputs.forwardVisitor { v, m ->
            NoNewSrcVisitor(v, m)
        }
    }

    override fun clearForwardVisitor() {
        checkFinalized()
        inputs.clearForwardVisitor()
    }

    override fun outputs(namespace: String, named: Boolean, canRemapTo: () -> List<String>): TempMappingNamespace {
        checkFinalized()
        if (namespace.lowercase() == mappingsConfig.OFFICIAL.name) {
            return object : TempMappingNamespace(namespace, named, canRemapTo) {
                override val actualNamespace: MappingNamespaceTree.Namespace
                    get() = mappingsConfig.OFFICIAL.also {
                        inputs.addNs(it.name)
                    }
            }.also {
                outputs.add(it)
            }
        }
        return object : TempMappingNamespace(namespace, named, canRemapTo) {
            override val actualNamespace by lazy {
                mappingsConfig.addNamespace(namespace, { canRemapTo().map { mappingsConfig.getNamespace(it.lowercase()) }.toSet() }, named).also {
                    inputs.addNs(it.name)
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