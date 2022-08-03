package xyz.wagyourtail.unimined.providers.minecraft.version

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.gradle.api.JavaVersion
import xyz.wagyourtail.unimined.OSUtils
import xyz.wagyourtail.unimined.SemVerUtils
import java.net.MalformedURLException
import java.net.URI
import java.nio.file.Path
import java.time.Instant

/*
 * Ported from https://github.com/wagyourtail/WagYourLauncher/blob/main/src/main/java/xyz/wagyourtail/launcher/minecraft/version/Version.java
 */
data class VersionData(
    val id: String,
    val type: String?,
    val time: Long,
    val releaseTime: Long,
    val minimumLauncherVersion: Int,
    val mainClass: String,
    val assetIndex: AssetIndex?,
    val assets: String?,
    val downloads: Map<String, Download>,
    val javaVersion: JavaVersion,
    val Arguments: Arguments?,
    val minecraftArguments: String?,
    val libraries: List<Library>,
)

fun parseVersionData(json: JsonObject): VersionData {
    return VersionData(
        json.get("id").asString,
        json.get("type")?.asString,
        json.get("time")?.asString?.let { Instant.parse(it).toEpochMilli() } ?: 0,
        json.get("releaseTime")?.asString?.let { Instant.parse(it).toEpochMilli() } ?: 0,
        json.get("minimumLauncherVersion")?.asInt ?: 0,
        json.get("mainClass").asString,
        json.get("assetIndex")?.asJsonObject?.let { parseAssets(it) },
        json.get("assets")?.asString,
        json.get("downloads").asJsonObject?.let { parseAllDownload(it) } ?: mapOf(),
        json.get("javaVersion")?.asJsonObject?.let { parseJavaVersion(it) } ?: JavaVersion.VERSION_1_8,
        json.get("Arguments")?.asJsonObject?.let { parseArguments(it) },
        json.get("minecraftArguments")?.asString,
        json.get("libraries")?.asJsonArray?.let { parseAllLibraries(it) } ?: listOf()
    )
}

data class AssetIndex(
    val id: String?,
    val sha1: String?,
    val url: URI?,
    val size: Long,
    val totalSize: Long
)

fun parseAssets(json: JsonObject): AssetIndex {
    return AssetIndex(
        json.get("id")?.asString,
        json.get("sha1")?.asString,
        try {
            URI(json.get("url")?.asString ?: "")
        } catch (e: MalformedURLException) {
            null
        },
        json.get("size")?.asLong ?: 0,
        json.get("totalSize")?.asLong ?: 0
    )
}

data class Download(
    val sha1: String,
    val size: Long,
    val url: URI?
)

fun parseAllDownload(json: JsonObject): Map<String, Download> {
    return json.entrySet().associate {
        it.key to parseDownload(it.value.asJsonObject)
    }
}

fun parseDownload(json: JsonObject): Download {
    return Download(
        json.get("sha1")?.asString ?: "",
        json.get("size")?.asLong ?: 0,
        try {
            json.get("url")?.asString?.let { URI(it) }
        } catch (e: MalformedURLException) {
            null
        }
    )
}

fun parseJavaVersion(json: JsonObject): JavaVersion {
    return JavaVersion.values()[json.get("majorVersion")?.asInt?.let { it - 1 } ?: 7]
}

data class Arguments(
    val jvm: List<Argument>,
    val game: List<Argument>
)

fun parseArguments(json: JsonObject): Arguments {
    return Arguments(
        json.get("jvm")?.asJsonArray?.let { parseArgumentList(it) } ?: listOf(),
        json.get("game")?.asJsonArray?.let { parseArgumentList(it) } ?: listOf(),
    )
}

fun parseArgumentList(json: JsonArray): List<Argument> {
    return json.map { parseArgument(it) }
}

data class Argument(
    val rules: List<Rule>,
    val values: List<String>
)

fun parseArgument(json: JsonElement): Argument {
    return if (json.isJsonObject) {
        parseArgument(json.asJsonObject)
    } else {
        Argument(
            listOf(),
            listOf(json.asString)
        )
    }
}

fun parseArgument(json: JsonObject): Argument {
    return Argument(
        json.getAsJsonArray("rules")?.map { parseRule(it.asJsonObject) } ?: listOf(),
        json.getAsJsonArray("values")?.map { it.asString } ?: listOf()
    )
}

data class Rule(
    val action: String?,
    val os: OperatingSystem?,
    val features: Map<String, Boolean>
) {
    fun testRule(): Boolean {
        if (os != null && !os.test()) return action != "allow"
//        features.forEach { (key, value) ->
//            if (launcher.features.contains(key) !== value) return action != "allow"
//        }
        return action == "allow"
    }
}

fun parseRule(json: JsonObject): Rule {
    return Rule(
        json.get("action")?.asString,
        json.get("os")?.asJsonObject?.let { parseOperatingSystem(it) },
        json.get("features")?.asJsonObject?.entrySet()?.associate { it.key to it.value.asBoolean } ?: mapOf()
    )
}

data class OperatingSystem(
    val name: String?,
    val version: String?,
    val arch: String?
) {
    fun test(): Boolean {
        if (name != null && name != OSUtils.oSId) return false
        return if ((version != null) && !SemVerUtils.matches(
                OSUtils.osVersion,
                version
            )
        ) false else arch == null || arch == OSUtils.osArch
    }
}

fun parseOperatingSystem(json: JsonObject): OperatingSystem {
    return OperatingSystem(
        json.get("name")?.asString,
        json.get("version")?.asString,
        json.get("arch")?.asString
    )
}

data class Library(
    val downloads: Downloads?,
    val name: String,
    val url: URI?,
    val natives: Map<String, String>,
    val extract: Extract,
    val rules: List<Rule>
)

fun parseAllLibraries(json: JsonArray): List<Library> {
    return json.map { parseLibrary(it.asJsonObject) }
}

fun parseLibrary(json: JsonObject): Library {
    return Library(
        json.get("downloads")?.asJsonObject?.let { parseDownloads(it) },
        json.get("name")?.asString ?: "",
        try {
            json.get("url")?.asString?.let { URI(it) }
        } catch (e: MalformedURLException) {
            null
        },
        json.get("natives")?.asJsonObject?.entrySet()?.associate { it.key to it.value.asString } ?: mapOf(),
        parseExtract(json.get("extract")?.asJsonObject ?: JsonObject()),
        json.getAsJsonArray("rules")?.map { parseRule(it.asJsonObject) } ?: listOf()
    )
}

data class Downloads(
    val artifact: Artifact?,
    val classifiers: Map<String, Artifact>
)

fun parseDownloads(json: JsonObject): Downloads {
    return Downloads(
        json.get("artifact")?.asJsonObject?.let { parseArtifact(it) },
        json.get("classifiers")?.asJsonObject?.entrySet()?.associate { it.key to parseArtifact(it.value.asJsonObject) } ?: mapOf()
    )
}

data class Artifact(
    val path: Path?,
    val sha1: String?,
    val size: Long,
    val url: URI?
)

fun parseArtifact(json: JsonObject): Artifact {
    return Artifact(
        json.get("path")?.asString?.let { Path.of(it) },
        json.get("sha1")?.asString,
        json.get("size")?.asLong ?: 0,
        try {
            json.get("url")?.asString?.let { URI(it) }
        } catch (e: MalformedURLException) {
            null
        }
    )
}

data class Extract(
    val exclude: List<String>
)

fun parseExtract(json: JsonObject): Extract {
    return Extract(
        json.getAsJsonArray("exclude")?.map { it.asString } ?: listOf()
    )
}