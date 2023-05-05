package xyz.wagyourtail.unimined.internal.mapping

import org.gradle.api.artifacts.ExternalModuleDependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig

class ExternalMappingDepImpl(dep: ExternalModuleDependency) : MappingDepConfig<ExternalModuleDependency>(dep), ExternalModuleDependency by dep {

}