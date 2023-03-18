package xyz.wagyourtail.unimined.minecraft.patch.forge.fg3.mcpconfig

import com.google.gson.JsonObject
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import java.net.URI
import java.net.URL

data class MCPConfig(
    val spec: Int,
    val version: String,
    val data: ConfigData,
    val steps: Map<EnvType, List<ConfigStep>>,
    val functions: Map<String, ConfigFunction>
)

fun parseConfig(mcpConfig: JsonObject): MCPConfig {
    val spec = mcpConfig["spec"].asInt
    if (spec != 1) {
        throw IllegalArgumentException("Unsupported MCPConfig spec version: $spec")
    }
    return MCPConfig(
        mcpConfig["spec"].asInt,
        mcpConfig["version"].asString,
        parseData(mcpConfig["data"].asJsonObject),
        parseSteps(mcpConfig["steps"].asJsonObject),
        parseFunctions(mcpConfig["functions"].asJsonObject)
    )
}

data class ConfigData(
    val access: String,
    val constructors: String,
    val exceptions: String,
    val mappings: String,
    val inject: String,
    val statics: String,
    val patches: Map<String, String>
)

private fun parseData(data: JsonObject): ConfigData =
    ConfigData(
        data["access"].asString,
        data["constructors"].asString,
        data["exceptions"].asString,
        data["mappings"].asString,
        data["inject"].asString,
        data["statics"].asString,
        data["patches"].asJsonObject.entrySet().associate { it.key to it.value.asString }
    )

data class ConfigStep(
    val type: String,
    val args: Map<String, String>
)

private fun parseSteps(steps: JsonObject): Map<EnvType, List<ConfigStep>> =
    steps.entrySet().associate { EnvType.parse(it.key) to it.value.asJsonArray.map { parseStep(it.asJsonObject) } }


private fun parseStep(step: JsonObject): ConfigStep =
    ConfigStep(
        step["type"].asString,
        step.asJsonObject.entrySet().filter { it.key != "type" }.associate { it.key to it.value.asString }
    )

data class ConfigFunction(
    val version: String,
    val args: List<String>,
    val jvmargs: List<String>,
    val repo: URL
)

private fun parseFunctions(functions: JsonObject): Map<String, ConfigFunction> =
    functions.entrySet().associate { it.key to parseFunction(it.value.asJsonObject) }

private fun parseFunction(function: JsonObject): ConfigFunction =
    ConfigFunction(
        function["version"].asString,
        function["args"].asJsonArray.map { it.asString },
        function["jvmargs"]?.asJsonArray?.map { it.asString } ?: emptyList(),
        URI(function["repo"].asString).toURL()
    )