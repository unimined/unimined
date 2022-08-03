package xyz.wagyourtail.unimined

import org.gradle.api.provider.Property

abstract class UniminedExtension {
    abstract val test: Property<String>

    init {

    }
}
