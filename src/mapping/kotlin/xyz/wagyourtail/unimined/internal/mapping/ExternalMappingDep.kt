package xyz.wagyourtail.unimined.internal.mapping

import org.gradle.api.artifacts.ExternalModuleDependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig

class ExternalMappingDepImpl(dep: ExternalModuleDependency, conf: MappingsProvider) : MappingDepConfig<ExternalModuleDependency>(dep, conf), ExternalModuleDependency by dep {

}