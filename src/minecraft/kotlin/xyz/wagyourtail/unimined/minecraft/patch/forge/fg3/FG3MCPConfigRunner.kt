package xyz.wagyourtail.unimined.minecraft.patch.forge.fg3

import com.google.gson.JsonParser
import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.minecraft.patch.forge.fg3.mcpconfig.ConfigFunction
import xyz.wagyourtail.unimined.minecraft.patch.forge.fg3.mcpconfig.ConfigStep
import xyz.wagyourtail.unimined.minecraft.patch.forge.fg3.mcpconfig.MCPConfig
import xyz.wagyourtail.unimined.minecraft.patch.forge.fg3.mcpconfig.parseConfig
import java.nio.file.Path

class FG3MCPConfigRunner(val project: Project, val parent: FG3MinecraftTransformer) {

    val dep: Dependency by lazy {
        project.dependencies.create("de.oceanlabs.cmp:mcp_config:${parent.provider.minecraft.version}@zip")
    }

    private val configFile: Path by lazy {
        project.configurations.detachedConfiguration(dep).resolve().first { it.extension == "zip" || it.extension == "jar" }.toPath()
    }

    val config: MCPConfig by lazy {
        ZipReader.readInputStreamFor("config.json", configFile) {
            parseConfig(JsonParser.parseReader(it.reader()).asJsonObject)
        }
    }

    fun executeMCP(toStep: String, outputPath: Path, envType: EnvType) {
        for (step in config.steps[envType]!!) {

        }
    }

    fun executeStep(step: ConfigStep, outputPath: Path, envType: EnvType) {
        if (config.functions.contains(step.type)) {
            executeFunction(config.functions[step.type]!!, step, outputPath, envType)
        }

        when (step.type) {

        }
    }

    fun executeFunction(function: ConfigFunction, step: ConfigStep, outputPath: Path, envType: EnvType) {

    }




}
