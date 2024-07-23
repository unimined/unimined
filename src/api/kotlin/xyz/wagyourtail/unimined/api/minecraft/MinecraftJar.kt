package xyz.wagyourtail.unimined.api.minecraft

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.util.classifier
import xyz.wagyourtail.unimined.util.plusAssign
import java.nio.file.Path

data class MinecraftJar(
    val parentPath: Path,
    val name: String,
    val envType: EnvType,
    val version: String,
    val patches: List<String>,
    val mappingNamespace: Namespace,
    val awOrAt: String?,
    val extension: String = "jar",
    val path: Path = parentPath.let {
        val pathBuilder = StringBuilder(name)
        for (part in JarNameParts.entries) {
            when (part) {
                JarNameParts.ENV -> if (envType.classifier != null) pathBuilder += "-${envType.classifier}"
                JarNameParts.PATCHES -> if (patches.isNotEmpty()) pathBuilder += "-${patches.joinToString("+")}"
                JarNameParts.VERSION -> pathBuilder += "-$version"
                JarNameParts.MAPPING -> pathBuilder += "-${mappingNamespace.name}"
                JarNameParts.AW_AT -> if (awOrAt != null) pathBuilder += "-$awOrAt"
                JarNameParts.EXTENSION -> pathBuilder += ".$extension"
                else -> {}
            }
        }
        it.resolve(pathBuilder.toString())
    }
) {

    constructor(
        from: MinecraftJar,
        parentPath: Path = from.parentPath,
        name: String = from.name,
        envType: EnvType = from.envType,
        version: String = from.version,
        patches: List<String> = from.patches,
        mappingNamespace: Namespace = from.mappingNamespace,
        awOrAt: String? = from.awOrAt,
        extension: String = from.extension
    ): this(parentPath, name, envType, version, patches, mappingNamespace, awOrAt, extension) {
        require(
            from.parentPath != parentPath ||
                    from.name != name ||
                    from.envType != envType ||
                    from.version != version ||
                    from.patches != patches ||
                    from.mappingNamespace != mappingNamespace ||
                    from.awOrAt != awOrAt ||
                    from.extension != extension
        ) {
            "MinecraftJar constructor called with no changes"
        }
    }

    override fun toString(): String {
        return "MinecraftJar(path=$path)"
    }
}

enum class JarNameParts {
    MINECRAFT, ENV, VERSION, PATCHES, MAPPING, AW_AT, CLASSIFIER, EXTENSION
}