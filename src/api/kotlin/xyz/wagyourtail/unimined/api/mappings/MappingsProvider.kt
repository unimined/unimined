package xyz.wagyourtail.unimined.api.mappings

import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.artifacts.Configuration
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.EnvType

/**
 * Mappings block, contains methods for manipulation of mappings.
 *
 * @since 0.1.0
 *
 * usage:
 * ```groovy
 * mappings {
 *     getStub("CLIENT").withMappings(["searge", "named"]) {
 *          c("ModLoader", "ModLoader", "modloader/ModLoader")
 *          c("BaseMod", "BaseMod", "modloader/BaseMod")
 *     }
 * }
 * ```
 */
abstract class MappingsProvider {

    /**
     * Get a stub mapping provider for the given environment.
     */
    fun getStub(envType: String) = getStub(EnvType.valueOf(envType))

    @ApiStatus.Internal
    abstract fun getStub(envType: EnvType): MemoryMapping

    @ApiStatus.Internal
    abstract fun hasStubs(envType: EnvType): Boolean

    @ApiStatus.Internal
    abstract fun getMappings(envType: EnvType): Configuration

    @ApiStatus.Internal
    abstract fun getMappingTree(envType: EnvType): MappingTreeView

    @ApiStatus.Internal
    abstract fun getCombinedNames(envType: EnvType): String

    @ApiStatus.Internal
    abstract fun getMappingProvider(
        envType: EnvType,
        srcName: String,
        fallbackSrc: String,
        fallbackTarget: String,
        targetName: String,
        remapLocalVariables: Boolean = true,
    ): (IMappingProvider.MappingAcceptor) -> Unit

}