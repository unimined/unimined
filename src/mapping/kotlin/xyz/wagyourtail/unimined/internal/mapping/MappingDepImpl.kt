package xyz.wagyourtail.unimined.internal.mapping

import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig

class MappingDepImpl(dep: Dependency) : MappingDepConfig<Dependency>(dep), Dependency by dep {

}