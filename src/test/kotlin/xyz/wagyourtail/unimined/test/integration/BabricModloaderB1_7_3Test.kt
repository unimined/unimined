package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.util.runTestProject

class BabricModloaderB1_7_3Test {
    @Test
    fun test_babric_modloader_b1_7_3() {
        val result = runTestProject("b1.7.3-Babric-Modloader")

        try {
            result.task(":build")?.outcome?.let {
                if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
            } ?: throw Exception("build failed")
        } catch (e: Exception) {
            println(result.output)
            throw Exception(e)
        }
    }
}