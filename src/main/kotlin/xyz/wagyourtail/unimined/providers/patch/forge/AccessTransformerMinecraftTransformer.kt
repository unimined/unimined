package xyz.wagyourtail.unimined.providers.patch.forge

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.getSha1
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.patch.AbstractMinecraftTransformer
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

    private val ats by lazy {
        val ats = provider.parent.getLocalCache().resolve("accessTransformers.cfg")
        ats.writeText(
            transformLegacyTransformer(transformers.joinToString("\n")),
            options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        )
        ats
    }

    override fun transform(envType: EnvType, baseMinecraft: Path): Path {
        val atjar = dynamicTransformerDependencies.files(atDep).first { it.extension == "jar" }
        val outFile = getOutputJarLocation(baseMinecraft)
        if (outFile.exists()) {
            return outFile
        }
        val retVal = runJarInSubprocess(
            atjar.toPath(),
            "-inJar", baseMinecraft.toString(),
            "-atFile", ats.toString(),
            "-outJar", outFile.toString(),
        )
        if (retVal != 0) {
            throw RuntimeException("AccessTransformer failed with exit code $retVal")
        }
        return outFile
    }

    fun getOutputJarLocation(baseMinecraft: Path): Path {
        return provider.parent.getLocalCache()
            .resolve("${baseMinecraft.fileName}-at-${ats.getSha1().substring(0..8)}.jar")
    }

    fun transformLegacyTransformer(file: String): String {
        var file = file
        // transform methods
        val legacyMethod = Regex("^(\\w+(?:-f)?)\\s([\\w.]+)\\.([\\w*<>]+)(\\(.+)\$", RegexOption.MULTILINE)
        file = file.replace(legacyMethod) {
            "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}${it.groupValues[4]}"
        }

        // transform fields
        val legacyField = Regex("^(\\w+(?:-f)?)\\s([\\w.]+)\\.([\\w*<>]+)\\s*(?:#.+)?\$", RegexOption.MULTILINE)
        file = file.replace(legacyField) {
            "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}"
        }
        return file
    }

}