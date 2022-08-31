package xyz.wagyourtail.unimined.providers.minecraft.patch.forge

import net.fabricmc.mappingio.tree.MappingTreeView
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.configurationcache.extensions.capitalized
import org.slf4j.LoggerFactory
import xyz.wagyourtail.unimined.getSha1
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.runJarInSubprocess
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class AccessTransformerMinecraftTransformer(project: Project, provider: MinecraftProvider) :
        AbstractMinecraftTransformer(project, provider) {
    companion object {
        val atDeps: MutableMap<Project, Dependency> = mutableMapOf()
    }

    val logger = LoggerFactory.getLogger(AccessTransformerMinecraftTransformer::class.java)

    private val transformers = mutableListOf<String>()

    private val atDep = atDeps.computeIfAbsent(project) {
        val dep = project.dependencies.create(
            "net.minecraftforge:accesstransformers:8.0.7:fatjar"
        )
        dynamicTransformerDependencies.dependencies.add(dep)
        dep
    }

    fun addAccessTransformer(stream: InputStream) {
        transformers.add(stream.readBytes().toString(StandardCharsets.UTF_8))
    }

    fun addAccessTransformer(path: Path) {
        transformers.add(path.readText())
    }

    fun addAccessTransformer(file: File) {
        transformers.add(file.readText())
    }

    private val ats = mutableMapOf<EnvType, Path>()
    fun atFile(envType: EnvType): Path {
        if (ats.containsKey(envType)) return ats[envType]!!
        ats[envType] = provider.parent.getLocalCache().resolve("accessTransformers${envType.name.capitalized()}.cfg")
        ats[envType]!!.writeText(
            remapTransformer(
                envType,
                transformLegacyTransformer(transformers.joinToString("\n")),
                "named", "searge", "official", "official"
            ),
            options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        )
        return ats[envType]!!
    }

    override fun transform(envType: EnvType, baseMinecraft: Path): Path {
        if (transformers.isEmpty()) {
            return baseMinecraft
        }
        val atjar = dynamicTransformerDependencies.files(atDep).first { it.extension == "jar" }
        val outFile = getOutputJarLocation(envType, baseMinecraft)
        if (outFile.exists()) {
            return outFile
        }
        val retVal = runJarInSubprocess(
            atjar.toPath(),
            "-inJar", baseMinecraft.toString(),
            "-atFile", atFile(envType).toString(),
            "-outJar", outFile.toString(),
        )
        if (retVal != 0) {
            throw RuntimeException("AccessTransformer failed with exit code $retVal")
        }
        return outFile
    }

    fun getOutputJarLocation(envType: EnvType, baseMinecraft: Path): Path {
        return provider.parent.getLocalCache()
            .resolve("${baseMinecraft.fileName}-at-${atFile(envType).getSha1().substring(0..8)}.jar")
    }

    fun transformLegacyTransformer(file: String): String {
        var file = file
        // transform methods
        val legacyMethod = Regex("^(\\w+(?:[\\-+]f)?)\\s([\\w.$]+)\\.([\\w*<>]+)(\\(.+)\$", RegexOption.MULTILINE)
        file = file.replace(legacyMethod) {
            "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}${it.groupValues[4]}"
        }

        // transform fields
        val legacyField = Regex("^(\\w+(?:[\\-+]f)?)\\s([\\w.$]+)\\.([\\w*<>]+)\\s*(?:#.+)?\$", RegexOption.MULTILINE)
        file = file.replace(legacyField) {
            "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}"
        }
        return file
    }

    fun remapTransformer(
        envType: EnvType,
        atFile: String,
        sourceNamespace: String,
        fallbackSrc: String,
        targetNamespace: String,
        fallbackTarget: String
    ): String {
        val mappings = provider.parent.mappingsProvider.getMappingTree(envType)

        val srcId = mappings.getNamespaceId(sourceNamespace)

        if (srcId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw RuntimeException("Invalid source namespace $sourceNamespace")
        }

        val targetId = mappings.getNamespaceId(targetNamespace)
        if (targetId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw RuntimeException("Invalid target namespace $targetNamespace")
        }

        val fallbackSrcId = mappings.getNamespaceId(fallbackSrc).let {
            if (it == MappingTreeView.NULL_NAMESPACE_ID) {
                project.logger.warn("Invalid fallback source namespace $fallbackSrc")
                srcId
            } else {
                it
            }
        }

        val fallbackTargetId = mappings.getNamespaceId(fallbackTarget).let {
            if (it == MappingTreeView.NULL_NAMESPACE_ID) {
                project.logger.warn("Invalid fallback target namespace $fallbackTarget")
                targetId
            } else {
                it
            }
        }

//        remapper.readClassPathAsync(mc)
//        project.logger.warn("Remapping mods using $mc")
//        remapper.readClassPathAsync(*provider.mcLibraries.resolve().map { it.toPath() }.toTypedArray())
        var atFile = atFile
        val methodMatcher = Regex(
            "^(\\w+(?:[\\-+]f)?)\\s([\\w.$]+)\\s([\\w*<>]+)(\\(.+?)(\\s*#.+?)?\$",
            RegexOption.MULTILINE
        )
        atFile = atFile.replace(methodMatcher) {
            val src = it.groupValues[2].replace(".", "/")
            val name = it.groupValues[3]
            val desc = it.groupValues[4].replace(".", "/")

            val srcClass = mappings.getClass(src, srcId) ?: mappings.getClass(src, fallbackSrcId)
            val targetClassName = if (srcClass == null) {
                logger.error("Could not find class $src in namespace $sourceNamespace or $fallbackSrc")
                logger.warn(it.value)
                src
            } else {
                val remappedSrcClass: String? = srcClass.getName(targetId) ?: srcClass.getName(fallbackTargetId)
                if (remappedSrcClass == null) {
                    logger.error("Could not find class $src in namespace $targetNamespace or $fallbackTarget")
                    logger.warn(it.value)
                    src
                } else {
                    remappedSrcClass
                }
            }

            val targetMethod = srcClass?.getMethod(name, desc, srcId) ?: srcClass?.getMethod(name, desc, fallbackSrcId)

            val targetMethodName = if (name == "*" || name.contains(Regex("[<>]"))) {
                name
            } else {
                if (targetMethod == null) {
                    logger.error("Could not find method $name$desc in class $src in namespace $sourceNamespace or $fallbackSrc")
                    logger.warn(it.value)
                    name
                } else {
                    val remappedTargetMethod: String? = targetMethod.getName(targetId) ?: targetMethod.getName(
                        fallbackTargetId
                    )
                    if (remappedTargetMethod == null) {
                        logger.error("Could not find method $name$desc in class $src in namespace $targetNamespace or $fallbackTarget")
                        logger.warn(it.value)
                        name
                    } else {
                        remappedTargetMethod
                    }
                }
            }

            val targetMethodDesc = if (desc == "()") {
                "()"
            } else if (name.contains(Regex("[<>]"))) {
                mappings.mapDesc(desc, srcId, targetId)
            } else {
                if (targetMethod == null) {
                    logger.error("Could not find method desc $name$desc in class $src in namespace $sourceNamespace or $fallbackSrc")
                    logger.warn(it.value)
                    desc
                } else {
                    val remappedTargetMethodDesc: String? = targetMethod.getDesc(targetId)
                        ?: targetMethod.getDesc(fallbackTargetId)
                    if (remappedTargetMethodDesc == null) {
                        logger.error("Could not find method desc $name$desc in class $src in namespace $targetNamespace or $fallbackTarget")
                        logger.warn(it.value)
                        desc
                    } else {
                        remappedTargetMethodDesc
                    }
                }
            }

            "${it.groupValues[1]} ${
                targetClassName.replace(
                    "/",
                    "."
                )
            } $targetMethodName$targetMethodDesc${it.groupValues[5]}"
        }

        val fieldMatcher = Regex("^(\\w+(?:[\\-+]f)?)\\s([\\w.$]+)\\s([\\w*<>]+)(\\s*#.+?)?\$", RegexOption.MULTILINE)

        atFile = atFile.replace(fieldMatcher) {
            val src = it.groupValues[2].replace(".", "/")
            val name = it.groupValues[3]

            val srcClass = mappings.getClass(src, srcId) ?: mappings.getClass(src, fallbackSrcId)

            if (srcClass == null) {
                val fixedSrcClass = mappings.getClass("$src/$name", srcId) ?: mappings.getClass("$src/$name", fallbackSrcId) ?: mappings.getClass("$src/$name", targetId) ?: mappings.getClass("$src/$name", fallbackTargetId)
                if (fixedSrcClass != null) {
                    logger.info("legacy transform incorrectly transformed a class aw to a field $src/$name changing back")
                    return@replace "${it.groupValues[1]} ${src.replace("/", ".")}.$name${it.groupValues[4]}"
                }
            }

            val targetClassName = if (srcClass == null) {
                logger.error("Could not find class $src in namespace $sourceNamespace or $fallbackSrc")
                logger.warn(it.value)
                src
            } else {
                val remappedSrcClass: String? = srcClass.getName(targetId) ?: srcClass.getName(fallbackTargetId)
                if (remappedSrcClass == null) {
                    logger.error("Could not find class $src in namespace $targetNamespace or $fallbackTarget")
                    logger.warn(it.value)
                    src
                } else {
                    remappedSrcClass
                }
            }

            val targetFieldName = if (name == "*" || name.contains(Regex("[<>]"))) {
                name
            } else {
                val targetField = srcClass?.getField(name, null, srcId) ?: srcClass?.getField(name, null, fallbackSrcId)

                if (targetField == null) {
                    logger.error("Could not find field $name in class $src in namespace $sourceNamespace or $fallbackSrc")
                    logger.warn(it.value)
                    name
                } else {
                    val remappedTargetField: String? = targetField.getName(targetId) ?: targetField.getName(
                        fallbackTargetId
                    )
                    if (remappedTargetField == null) {
                        logger.error("Could not find field $name in class $src in namespace $targetNamespace or $fallbackTarget")
                        logger.warn(it.value)
                        name
                    } else {
                        remappedTargetField
                    }
                }
            }

            "${it.groupValues[1]} ${targetClassName.replace("/", ".")} $targetFieldName${it.groupValues[4]}"
        }

        val classMatcher = Regex("^(\\w+(?:[\\-+]f)?)\\s([\\w.$]+)(\\s*#.+?)?\$", RegexOption.MULTILINE)

        atFile = atFile.replace(classMatcher) {
            val src = it.groupValues[2].replace(".", "/")

            val srcClass = mappings.getClass(src, srcId) ?: mappings.getClass(src, fallbackSrcId)

            val targetClassName = if (srcClass == null) {
                logger.error("Could not find class $src in namespace $sourceNamespace or $fallbackSrc")
                logger.warn(it.value)
                src
            } else {
                val remappedSrcClass: String? = srcClass.getName(targetId) ?: srcClass.getName(fallbackTargetId)
                if (remappedSrcClass == null) {
                    logger.error("Could not find class $src in namespace $targetNamespace or $fallbackTarget")
                    logger.warn(it.value)
                    src
                } else {
                    remappedSrcClass
                }
            }

            "${it.groupValues[1]} ${targetClassName.replace("/", ".")}${it.groupValues[3]}"
        }

        return atFile
    }

}