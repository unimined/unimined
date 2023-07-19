package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.util.runTestProject

class ForgeFabric1_20Test {
    @Test
    @Disabled
    fun test_forge_fabric_1_20() {
        val result = runTestProject("1.20-Forge-Fabric")
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }
}