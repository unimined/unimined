package xyz.wagyourtail.unimined.providers.patch

import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import java.nio.file.Path

data class MinecraftJar(
    val parentPath: Path,
    val name: String,
    val envType: EnvType,
    val version: String,
    val patches: List<String>,
    val mappingNamespace: String,
    val fallbackNamespace: String,
    val awOrAt: String?,
    val extension: String = "jar",
    val path: Path = parentPath.let {
        var path = name
        for (part in JarNameParts.values()) {
            when (part) {
                JarNameParts.ENV -> if (envType.classifier != null) path += "-${envType.classifier}"
                JarNameParts.PATCHES -> if (patches.isNotEmpty()) path += "-${patches.joinToString("+")}"
                JarNameParts.VERSION -> path += "-$version"
                JarNameParts.MAPPING -> path += if (mappingNamespace != fallbackNamespace) "-$mappingNamespace+$fallbackNamespace" else "-$mappingNamespace"
                JarNameParts.AW_AT -> if (awOrAt != null) path += "-$awOrAt"
                JarNameParts.EXTENSION -> path += ".$extension"
                else -> {}
            }
        }
        it.resolve(path)
    }
) {

    constructor(
        from: MinecraftJar,
        parentPath: Path = from.parentPath,
        name: String = from.name,
        envType: EnvType = from.envType,
        version: String = from.version,
        patches: List<String> = from.patches,
        mappingNamespace: String = from.mappingNamespace,
        fallbackNamespace: String = from.fallbackNamespace,
        awOrAt: String? = from.awOrAt,
        extension: String = from.extension
    ) : this(parentPath, name, envType, version, patches, mappingNamespace, fallbackNamespace, awOrAt, extension) {
        assert(
            from.parentPath != parentPath ||
                    from.name != name ||
                    from.envType != envType ||
                    from.version != version ||
                    from.patches != patches ||
                    from.mappingNamespace != mappingNamespace ||
                    from.fallbackNamespace != fallbackNamespace ||
                    from.awOrAt != awOrAt ||
                    from.extension != extension
        )
        {
            "MinecraftJar constructor called with no changes"
        }
    }

    override fun toString(): String {
        return "MinecraftJar(path=$path)"
    }
}

enum class JarNameParts {
    MINECRAFT, ENV, VERSION, PATCHES, MAPPING, AW_AT, EXTENSION
}