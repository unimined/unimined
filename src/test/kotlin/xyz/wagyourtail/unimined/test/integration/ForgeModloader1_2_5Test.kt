package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.util.runTestProject

class ForgeModloader1_2_5Test {
    @Test
    fun test_forge_modloader_1_2_5() {
        val result = runTestProject("1.2.5-Forge-Modloader")

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