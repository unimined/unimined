package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.util.runTestProject

class NeoForgedForgeFabric1_20_2Test {
    @Test
    fun test_neoforged_fabric_1_20_2() {
        val result = runTestProject("1.20.2-NeoForged-Forge-Fabric")
        result.task(":build")?.outcome?.let {
            if (it != TaskOutcome.SUCCESS) throw Exception("build failed")
        } ?: throw Exception("build failed")
    }
}