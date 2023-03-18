package xyz.wagyourtail.unimined.api.minecraft

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class EnvType(val classifier: String?) {
    CLIENT("client"),
    SERVER("server"),
    COMBINED(null),
    ;

    companion object {
        fun parse(value: String) =
            when (value) {
                "client" -> CLIENT
                "server" -> SERVER
                "combined", "joined" -> COMBINED
                else -> throw IllegalArgumentException("Invalid environment type: $value")
            }
    }
}