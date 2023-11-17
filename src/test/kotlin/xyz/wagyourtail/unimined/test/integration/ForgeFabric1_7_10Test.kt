package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.util.runTestProject

class ForgeFabric1_7_10Test {
    @Test
    fun test_forge_fabric_1_7_10() {
        val result = runTestProject("1.7.10-Forge-Fabric")

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