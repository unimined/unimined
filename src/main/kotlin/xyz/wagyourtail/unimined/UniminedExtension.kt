package xyz.wagyourtail.unimined

import org.gradle.api.provider.Property

@Suppress("LeakingThis")
abstract class UniminedExtension {
    abstract val disableCombined: Property<Boolean>

    init {
        disableCombined.convention(false)
    }
}

