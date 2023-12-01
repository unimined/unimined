package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.util.runTestProject

class ForgeFabric1_16_5Test {
    @Test
    fun test_forge_fabric_1_16_5() {
        try {
            val result = runTestProject("1.16.5-Forge-Fabric")

            try {
                result.task(":build")?.outcome?.let {
                    if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
                } ?: throw Exception("build failed")
            } catch (e: Exception) {
                println(result.output)
                throw Exception(e)
            }
        } catch (e: UnexpectedBuildFailure) {
            println(e)
            throw Exception("build failed", e)
        }
    }
}