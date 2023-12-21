package xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit.buildtools

import com.google.gson.JsonObject
import java.net.URI

data class VersionInfo(
    val minecraftVersion: String,
    val serverUrl: URI,
    val mappingsUrl: String?,
    val minecraftHash: String,
    val accessTransformers: String?,
    val classMappings: String,
    val memberMappings: String?,
    val packageMappings: String?,
    val decompileCommand: String?,
    val classMapCommand: String,
    val memberMapCommand: String,
    val finalMapCommand: String,
    val toolsVersion: Int
) {
}

fun parseVersionInfo(json: JsonObject): VersionInfo {
    return VersionInfo(
        json["minecraftVersion"].asString,
        URI(json["serverUrl"].asString),
        json["mappingsUrl"]?.asString,
        json["minecraftHash"].asString,
        json["accessTransformers"]?.asString,
        json["classMappings"].asString,
        json["memberMappings"]?.asString,
        json["packageMappings"]?.asString,
        json["decompileCommand"]?.asString,
        json["classMapCommand"].asString,
        json["memberMapCommand"].asString,
        json["finalMapCommand"].asString,
        json["toolsVersion"].asInt
    )
}