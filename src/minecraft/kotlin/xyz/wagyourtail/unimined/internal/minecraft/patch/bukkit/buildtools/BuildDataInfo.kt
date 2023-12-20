package xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit.buildtools

import java.net.URI

data class BuildDataInfo(
    val minecraftVersion: String,
    val serverUrl: URI,
    val mappingsUrl: String,
    val minecraftHash: String,
    val accessTransformers: String,
    val classMappings: String,
    val memberMappings: String,
    val packageMappings: String,
    val decompileCommand: String,
    val classMapCommand: String,
    val memberMapCommand: String,
    val finalMapCommand: String,
    val toolsVersion: String
) {
}