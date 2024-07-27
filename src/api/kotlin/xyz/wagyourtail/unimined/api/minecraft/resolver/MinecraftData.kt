package xyz.wagyourtail.unimined.api.minecraft.resolver

import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.net.URI
import java.nio.file.Path

abstract class MinecraftData {

    @get:ApiStatus.Internal
    abstract val mcVersionFolder: Path

    @set:ApiStatus.Experimental
    abstract var launcherMetaUrl: URI?

    @set:ApiStatus.Experimental
    abstract var metadataURL: URI

    @get:ApiStatus.Internal
    abstract val officialClientMappingsFile: File

    @get:ApiStatus.Internal
    abstract val officialServerMappingsFile: File

    abstract fun mcVersionCompare(vers1: String, vers2: String): Int

    abstract val minecraftClientFile: File
    abstract val minecraftServerFile: File

    abstract val hasMappings: Boolean
}