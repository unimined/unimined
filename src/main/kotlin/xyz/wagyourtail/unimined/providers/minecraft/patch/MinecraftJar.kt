package xyz.wagyourtail.unimined.providers.minecraft.patch

import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import java.nio.file.Path

data class MinecraftJar(val jarPath: Path, val envType: EnvType, val mappingNamespace: String, val fallbackMappingNamespace: String) {

    constructor(
        from: MinecraftJar,
        jarPath: Path = from.jarPath,
        envType: EnvType = from.envType,
        mappingNamespace: String = from.mappingNamespace,
        fallbackMappingNamespace: String = from.fallbackMappingNamespace
    ) : this(jarPath, envType, mappingNamespace, fallbackMappingNamespace)
}