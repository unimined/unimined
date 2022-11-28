package xyz.wagyourtail.unimined

import org.jetbrains.annotations.ApiStatus
import java.net.URI

object Constants {
    const val MAPPINGS_PROVIDER = "mappings"

    const val MINECRAFT_COMBINED_PROVIDER = "minecraft"

    const val MINECRAFT_SERVER_PROVIDER = "minecraftServer"

    const val MINECRAFT_CLIENT_PROVIDER = "minecraftClient"

    @ApiStatus.Internal
    const val MINECRAFT_LIBRARIES_PROVIDER = "minecraftLibraries"

    const val MINECRAFT_MAVEN = "https://libraries.minecraft.net/"

    const val MINECRAFT_GROUP = "net.minecraft"

    const val MINECRAFT_FORGE_GROUP = "net.minecraftforge"

    const val JARMOD_PROVIDER = "jarMod"

    const val FABRIC_PROVIDER = "fabric"

    const val FORGE_PROVIDER = "forge"

    const val INCLUDE_PROVIDER = "include"

    val ASSET_BASE_URL: URI = URI.create("https://resources.download.minecraft.net/")

    val METADATA_URL: URI = URI.create("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")

    @ApiStatus.Internal
    const val FORGE_CLIENT_EXTRA = "forgeClientExtra"

    @ApiStatus.Internal
    const val MAPPINGS_INTERNAL = "mappingsInternal"

    @ApiStatus.Internal
    const val FORGE_DEPS = "forgeDeps"
}

