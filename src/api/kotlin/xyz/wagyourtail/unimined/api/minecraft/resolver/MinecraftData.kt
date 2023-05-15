package xyz.wagyourtail.unimined.api.minecraft.resolver

import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.net.URI

abstract class MinecraftData {

    /**
     * if before server/client are combined in 1.3+
     */
    @set:ApiStatus.Experimental
    abstract var isPreCombined: Boolean

    @set:ApiStatus.Experimental
    abstract var metadataURL: URI

    @get:ApiStatus.Internal
    abstract val officialClientMappingsFile: File

    @get:ApiStatus.Internal
    abstract val officialServerMappingsFile: File

}