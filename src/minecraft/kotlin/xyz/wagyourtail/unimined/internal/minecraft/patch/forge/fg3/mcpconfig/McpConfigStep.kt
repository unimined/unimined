package xyz.wagyourtail.unimined.internal.minecraft.patch.forge.fg3.mcpconfig

import com.google.common.collect.ImmutableMap
import com.google.gson.JsonObject

data class McpConfigStep(val type: String, val name: String, val config: Map<String, ConfigValue>) {
    companion object {
        private const val TYPE_KEY = "type"
        private const val NAME_KEY = "name"

        fun fromJson(json: JsonObject): McpConfigStep {
            val type = json[TYPE_KEY].asString
            val name = if (json.has(NAME_KEY)) json[NAME_KEY].asString else type
            val config = ImmutableMap.builder<String, ConfigValue>()
            for (key in json.keySet()) {
                if ((key == TYPE_KEY) || (key == NAME_KEY)) continue
                config.put(key, ConfigValue.of(json[key].asString))
            }
            return McpConfigStep(type, name, config.build())
        }
    }
}