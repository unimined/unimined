package xyz.wagyourtail.unimined.internal.mapping

import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig

class MappingDepImpl(dep: Dependency, conf: MappingsProvider) : MappingDepConfigImpl<Dependency>(dep, conf), Dependency by dep {

}