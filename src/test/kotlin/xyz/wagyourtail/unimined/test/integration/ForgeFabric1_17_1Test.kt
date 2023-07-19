package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.util.runTestProject

class ForgeFabric1_17_1Test {
    @Test
    fun test_forge_fabric_1_17_1() {
        val result = runTestProject("1.17.1-Forge-Fabric")
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }
}