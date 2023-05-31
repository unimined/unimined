package xyz.wagyourtail.unimined.internal.mapping

import net.fabricmc.mappingio.format.MappingTreeBuilder
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig

abstract class MappingDepConfigImpl<T: Dependency>(dep: T, mappingsConfig: MappingsConfig): MappingDepConfig<T>(dep,
    mappingsConfig
) {
    val inputs = MappingTreeBuilder.MappingInputBuilder()
    override fun mapNamespace(from: String, to: String) {
        inputs.mapNs(from, to)
    }

    override fun sourceNamespace(namespace: String) {
        inputs.setSource(namespace)
    }

    override fun sourceNamespace(mappingTypeToSrc: (String) -> String) {
        inputs.setSource {
            mappingTypeToSrc(it.name)
        }
    }

    override fun outputs(namespace: String, named: Boolean, canRemapTo: () -> List<String>): MappingNamespaceTree.Namespace {
        return mappingsConfig.addNamespace(namespace, { canRemapTo().map { mappingsConfig.getNamespace(it.lowercase()) }.toSet() }, named).also {
            inputs.addNs(it.name)
        }
    }
}