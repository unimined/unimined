package xyz.wagyourtail.unimined.internal.minecraft.task

import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.sources.GenSourcesTask
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.fg3.mcpconfig.SubprocessExecutor
import java.io.File
import java.net.URI
import java.nio.file.Path

abstract class GenSourcesTaskImpl(val provider: MinecraftProvider): GenSourcesTask() {

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

        val outputJar = provider.minecraftFileDev.parentFile.resolve("${provider.minecraftFileDev.nameWithoutExtension}-sources.jar")

        val args = resolvePlaceholders(args, provider.minecraftFileDev.toPath(), outputJar.toPath())

        val result = SubprocessExecutor.exec(project) { spec ->
            spec.workingDir(provider.minecraftFileDev.parentFile)
            spec.classpath(decompilerJar)
            spec.args(args)
        }

        if (result.exitValue != 0) {
            throw RuntimeException("Decompilation failed with exit code ${result.exitValue}")
        }
    }

    //TODO: gen mod sources

    private val placeholderPattern = Regex("\\{([^}]+?)}")

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