package xyz.wagyourtail.unimined.test.integration

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.util.runTestProject

class NeoForgedFabric1_20_1Test {
    @Test
    fun test_neoforged_fabric_1_20_1() {
        val result = runTestProject("1.20.1-NeoForged-Fabric")

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