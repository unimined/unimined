package xyz.wagyourtail.unimined.api.mappings

import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.EnvType

val Project.mappings
    get() = this.extensions.getByType(MappingsProvider::class.java)

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
    @Deprecated("use stub(envType: String) instead", ReplaceWith("stub(envType)"))
    fun getStub(envType: String) = getStub(EnvType.valueOf(envType))

    /**
     * Get a stub mapping provider for the given environment.
     * @since 0.5.0
     */
    fun stub(envType: String) = getStub(EnvType.valueOf(envType))

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
    abstract fun getMappingsProvider(
        envType: EnvType,
        remap: Pair<MappingNamespace, MappingNamespace>,
        remapLocalVariables: Boolean = true,
    ): (IMappingProvider.MappingAcceptor) -> Unit

    @ApiStatus.Internal
    abstract fun getAvailableMappings(envType: EnvType): Set<MappingNamespace>

}