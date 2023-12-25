package xyz.wagyourtail.unimined.util

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText

val GRADLE_CURRENT: String = GradleVersion.current().version;

fun runTestProject(name: String, gradle: String = GRADLE_CURRENT): BuildResult {
    return runGradle(getTestProjectPath(name), gradle)
}

fun getTestProjectPath(name: String): Path {
    return Paths.get(".").resolve("testing").resolve(name)
}

fun openZipFileSystem(project: String, path: String): FileSystem? {
    val fullPath = getTestProjectPath(project).resolve(path)

    if (!fullPath.exists()) return null

    return fullPath.openZipFileSystem(mapOf("mutable" to false))
}
class IntegrationTestUtils {
    companion object {
        private val GRADLE_VERSION = arrayOf(
//            "7.6.3",
            "8.1.1",
            "8.4",
            "8.5",
            GRADLE_CURRENT,
        )

        @JvmStatic
        fun versions() = GRADLE_VERSION;
    }
}

fun runGradle(dir: Path, version: String = GRADLE_CURRENT): BuildResult {
    println("Running gradle in $dir")
    val buildDir = dir.resolve("build")
    if (buildDir.exists()) buildDir.deleteRecursively()

    val settings = dir.resolve("settings.gradle")
    if (settings.exists()) {
        val lines = settings.toFile().readLines().toMutableList()
        val includeBuild = lines.indexOfFirst { it.startsWith("// includeBuild") }
        if (includeBuild != -1) {
            lines[includeBuild] = lines[includeBuild].substring(3)
            settings.writeText(lines.joinToString("\n"))
        }
    }

    val uniminedDir = dir.resolve(".gradle").resolve("unimined")
    if (uniminedDir.exists()) uniminedDir.deleteRecursively()

    val classpath = System.getProperty("java.class.path").split(File.pathSeparatorChar).map { File(it) }
    val result = GradleRunner.create()
        .withGradleVersion(version)
        .withProjectDir(dir.toFile())
        .withArguments("clean", "build", "--stacktrace", "--info", "--refresh-dependencies")
        .withPluginClasspath(classpath)
        .build()
    if (settings.exists()) {
        val lines = settings.toFile().readLines().toMutableList()
        val includeBuild = lines.indexOfFirst { it.startsWith("// includeBuild") }
        if (includeBuild != -1) {
            lines[includeBuild] = lines[includeBuild].substring(3)
            settings.writeText(lines.joinToString("\n"))
        }
    }
    return result
}