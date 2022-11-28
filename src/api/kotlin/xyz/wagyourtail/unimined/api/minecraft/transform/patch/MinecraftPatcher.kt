package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import org.gradle.api.provider.Property

abstract class MinecraftPatcher {
    abstract val name: String

    abstract val prodNamespace: String
    abstract val prodFallbackNamespace: String

    abstract val devNamespace: Property<String>
    abstract val devFallbackNamespace: Property<String>

}