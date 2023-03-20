package xyz.wagyourtail.unimined.sources

import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.sources.GenSourcesTask
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.minecraft.patch.forge.fg3.mcpconfig.SubprocessExecutor
import java.io.File
import java.net.URI
import java.nio.file.Path

abstract class GenSourcesTaskImpl(): GenSourcesTask() {
    private val minecraftProvider = project.extensions.getByType(MinecraftProviderImpl::class.java)
    private val modProvider = project.extensions.getByType(UniminedExtension::class.java).modProvider

    @TaskAction
    private fun run() {
        val decompiler = decompiler.get()

        // add quilt maven
        project.repositories.maven {
            it.url = URI.create("https://maven.quiltmc.org/repository/release")
        }

        val decompilerJar = project.configurations.detachedConfiguration(
            project.dependencies.create(decompiler)
        ).resolve().first { it.extension == "jar" }

        val jarsToDecompile = mutableSetOf<File>()

        for (envType in EnvType.values()) {
            val mcConfig = minecraftProvider.getMinecraftConfig(envType)
            if (mcConfig.isEmpty) continue

            if (decompileMods.get()) {
                TODO()
            }

            jarsToDecompile.add(mcConfig.resolve().first { it.extension == "jar" })
        }

        project.logger.info("Decompiling ${jarsToDecompile.size} jars with $decompiler")
        project.logger.debug("Decompiling jars: $jarsToDecompile")

        for (inputJar in jarsToDecompile) {
            val outputJar = inputJar.parentFile.resolve("${inputJar.nameWithoutExtension}-sources.jar")

            val args = resolvePlaceholders(args, inputJar.toPath(), outputJar.toPath())

            val result = SubprocessExecutor.exec(project) { spec ->
                spec.workingDir(inputJar.parentFile)
                spec.classpath(decompilerJar)
                spec.args(args)
            }

            if (result.exitValue != 0) {
                throw RuntimeException("Decompilation failed with exit code ${result.exitValue}")
            }
        }
    }

    private val placeholderPattern = Regex("\\{([^}]+)}")

    private fun resolvePlaceholders(args: List<String>, input: Path, output: Path): List<String> {
        return args.map { arg ->
            placeholderPattern.replace(arg) {
                if (it.groupValues[1].startsWith("configurations.")) {
                    val configName = it.groupValues[1].substringAfter("configurations.")
                    val config = project.configurations.getByName(configName)
                    val resolved = config.resolve()
                    resolved.joinToString(separator = File.pathSeparator) { it.absolutePath }
                } else {
                    when (it.groupValues[1]) {
                        "inputJar" -> input.toAbsolutePath().toString()
                        "outputJar" -> output.toAbsolutePath().toString()
                        else -> throw IllegalArgumentException("Unknown placeholder: ${it.groupValues[1]}")
                    }
                }
            }
        }
    }
}