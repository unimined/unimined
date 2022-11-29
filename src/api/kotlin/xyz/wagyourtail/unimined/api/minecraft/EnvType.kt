package xyz.wagyourtail.unimined.api.minecraft

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class EnvType(val classifier: String?) {
    CLIENT("client"),
    SERVER("server"),
    COMBINED(null),
    ;
}