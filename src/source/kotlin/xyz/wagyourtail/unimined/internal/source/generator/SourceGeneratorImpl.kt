package xyz.wagyourtail.unimined.internal.source.generator

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import xyz.wagyourtail.unimined.api.source.generator.SourceGenerator
import xyz.wagyourtail.unimined.internal.source.SourceProvider
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.withSourceSet
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class SourceGeneratorImpl(val project: Project, val provider: SourceProvider) : SourceGenerator {

    override var args: List<String> by FinalizeOnRead(listOf(
        "-jrt=1"
    ))

    override var linemaps: Boolean by FinalizeOnRead(false)

    val generator = project.configurations.maybeCreate("sourceGenerator".withSourceSet(provider.minecraft.sourceSet))

    override fun generator(dep: Any, action: Dependency.() -> Unit) {
        generator.dependencies.add(
            project.dependencies.create(
                if (dep is String && !dep.contains(":")) {
                    "org.vineflower:vineflower:$dep"
                } else {
                    dep
                }
            )
            .also {
                action(it)
            }
        )

    }

    override fun generate(classpath: FileCollection, inputPath: Path) {
        if (generator.dependencies.isEmpty()) {
            generator("1.9.3")
        }

        val outputFile = inputPath.resolveSibling(inputPath.nameWithoutExtension + "-sources." + inputPath.extension)

        project.javaexec { spec ->

            val toolchain = project.extensions.getByType(JavaToolchainService::class.java)
            spec.executable = toolchain.launcherFor {
                it.languageVersion.set(JavaLanguageVersion.of(11))
            }.orElse(
                toolchain.launcherFor {
                    it.languageVersion.set(JavaLanguageVersion.of(17))
                }
            ).get().executablePath.asFile.absolutePath

            spec.classpath(generator)
            val args = args.toMutableList()
            if (linemaps) {
                args += "-bsm=1"
                args += "-dcl=1"
            }
            args += listOf(
                "-e=" + classpath.joinToString(File.pathSeparator) { it.absolutePath },
                inputPath.absolutePathString(),
                "--file",
                outputFile.absolutePathString()
            )

            spec.args = args

        }
    }


}
