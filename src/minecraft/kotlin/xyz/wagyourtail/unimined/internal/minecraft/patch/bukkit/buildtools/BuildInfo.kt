package xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit.buildtools

import com.google.gson.JsonObject

data class BuildInfo(
    val name: String,
    val description: String,
    val toolsVersion: Int,
    val javaVersions: List<Int>,
    val refs: Refs
) {
}

data class Refs(
    val buildData: String,
    val bukkit: String,
    val craftbukkit: String,
    val spigot: String
) {
}

fun parseBuildInfo(json: JsonObject): BuildInfo {
    return BuildInfo(
        json["name"].asString,
        json["description"].asString,
        json["toolsVersion"].asInt,
        json["javaVersions"].asJsonArray.map { it.asInt },
        parseRefs(json["refs"].asJsonObject)
    )
}

fun parseRefs(json: JsonObject): Refs {
    return Refs(
        json["BuildData"].asString,
        json["Bukkit"].asString,
        json["CraftBukkit"].asString,
        json["Spigot"].asString
    )
}