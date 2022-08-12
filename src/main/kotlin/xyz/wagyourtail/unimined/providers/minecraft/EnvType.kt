package xyz.wagyourtail.unimined.providers.minecraft

enum class EnvType(val classifier: String?) {
    CLIENT("client"),
    SERVER("server"),
    COMBINED(null),
    ;
}