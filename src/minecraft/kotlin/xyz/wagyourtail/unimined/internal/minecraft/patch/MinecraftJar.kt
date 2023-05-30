package xyz.wagyourtail.unimined.internal.minecraft.patch

import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.util.plusAssign
import java.nio.file.Path

data class MinecraftJar(
    val parentPath: Path,
    val name: String,
    val envType: EnvType,
    val version: String,
    val patches: List<String>,
    val mappingNamespace: MappingNamespaceTree.Namespace,
    val fallbackNamespace: MappingNamespaceTree.Namespace,
    val awOrAt: String?,
    val extension: String = "jar",
    val path: Path = parentPath.let {
        val pathBuilder = StringBuilder(name)
        for (part in JarNameParts.values()) {
            when (part) {
                JarNameParts.ENV -> if (envType.classifier != null) pathBuilder += "-${envType.classifier}"
                JarNameParts.PATCHES -> if (patches.isNotEmpty()) pathBuilder += "-${patches.joinToString("+")}"
                JarNameParts.VERSION -> pathBuilder += "-$version"
                JarNameParts.MAPPING -> pathBuilder += if (mappingNamespace != fallbackNamespace) "-${mappingNamespace.name}+${fallbackNamespace.name}" else "-${mappingNamespace.name}"
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
        mappingNamespace: MappingNamespaceTree.Namespace = from.mappingNamespace,
        fallbackNamespace: MappingNamespaceTree.Namespace = from.fallbackNamespace,
        awOrAt: String? = from.awOrAt,
        extension: String = from.extension
    ): this(parentPath, name, envType, version, patches, mappingNamespace, fallbackNamespace, awOrAt, extension) {
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