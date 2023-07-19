package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.util.deleteRecursively
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*


class IntegrationTests {

    fun runGradle(dir: Path): BuildResult {
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
            .withProjectDir(dir.toFile())
            .withArguments("clean", "build", "--stacktrace", "--info")
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

    @Test
    fun test1_2_5() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.2.5-Forge-Modloader"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun test1_3_2() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.3.2-Forge"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun test1_6_4() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.6.4-Forge"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun test1_7_10() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.7.10-Forge-Fabric"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun test1_8_9() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.8.9-Forge-Fabric"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun test1_12_2() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.12.2-Forge-Fabric"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun test1_14_4() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.14.4-Forge-Fabric"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun test1_15_2() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.15.2-Forge-Fabric"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    @Disabled
    fun test1_16_5() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.16.5-Forge-Fabric"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun test1_17_1() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.17.1-Forge-Fabric"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    @Disabled
    fun test1_20() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.20-Forge-Fabric"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun test1_20_1() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("1.20.1-NeoForged-Fabric"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }

    @Test
    fun testb1_7_3() {
        val result = runGradle(Paths.get(".").resolve("testing").resolve("b1.7.3-Babric-Modloader"))
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }



}