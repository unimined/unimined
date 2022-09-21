package xyz.wagyourtail.unimined

import org.jetbrains.annotations.ApiStatus
import java.net.URI

object Constants {
    val MAPPINGS_PROVIDER = "mappings"

    const val MINECRAFT_COMBINED_PROVIDER = "minecraft"

    const val MINECRAFT_SERVER_PROVIDER = "minecraftServer"

    const val MINECRAFT_CLIENT_PROVIDER = "minecraftClient"

    const val MINECRAFT_LIBRARIES_PROVIDER = "minecraftLibraries"

    const val MINECRAFT_MAVEN = "https://libraries.minecraft.net/"

    const val MINECRAFT_GROUP = "net.minecraft"

    const val JARMOD_PROVIDER = "jarMod"

    const val FABRIC_PROVIDER = "fabric"

    const val FORGE_PROVIDER = "forge"

    val ASSET_BASE_URL: URI = URI.create("https://resources.download.minecraft.net/")

    val METADATA_URL: URI = URI.create("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")

    @ApiStatus.Internal
    val DYNAMIC_TRANSFORMER_DEPENDENCIES = "dynamicTransformerDependencies"

    @ApiStatus.Internal
    const val FABRIC_JSON = "fabricHiddenDontTouch"

    @ApiStatus.Internal
    const val MAPPINGS_INTERNAL = "mappingsHiddenDontTouch"

    @ApiStatus.Internal
    const val FORGE_DEPS = "forgeDeps"

    @ApiStatus.Internal
    const val FORGE_USERDEV = "forgeUserdev"

    const val FORGE_CLIENT_EXTRA = "forgeClientExtra"

    @ApiStatus.Internal
    const val FORGE_INSTALLER = "forgeInstaller"

    @ApiStatus.Internal
    const val OFFICIAL_MAPPINGS_INTERNAL = "officialMappingsHiddenDontTouch"
}

