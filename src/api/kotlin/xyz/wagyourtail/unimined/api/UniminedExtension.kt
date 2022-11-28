package xyz.wagyourtail.unimined.api

import org.gradle.api.provider.Property

abstract class UniminedExtension {
    abstract val useGlobalCache: Property<Boolean>

    init {
        @Suppress("LeakingThis")
        useGlobalCache.convention(true).finalizeValueOnRead()
    }
}