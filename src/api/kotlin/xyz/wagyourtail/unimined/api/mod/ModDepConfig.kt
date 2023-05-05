package xyz.wagyourtail.unimined.api.mod

import org.gradle.api.artifacts.ExternalModuleDependency

class ModDepConfig(val emd: ExternalModuleDependency) : ExternalModuleDependency by emd {



}