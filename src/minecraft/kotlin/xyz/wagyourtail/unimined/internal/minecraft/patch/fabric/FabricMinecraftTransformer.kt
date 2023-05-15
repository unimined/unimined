package xyz.wagyourtail.unimined.internal.minecraft.patch.fabric

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.patch.fabric.FabricApiExtension
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import java.net.URI

open class FabricMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider
): FabricLikeMinecraftTransformer(
    project,
    provider,
    "fabric",
    "fabric.mod.json",
    "accessWidener"
) {

    override val ENVIRONMENT: String = "Lnet/fabricmc/api/Environment;"
    override val ENV_TYPE: String = "Lnet/fabricmc/api/EnvType;"

    init {
        setupApiExtension()
    }

    open fun setupApiExtension() {
        FabricApiExtension.apply(project)
    }

    override fun addMavens() {
        project.repositories.maven {
            it.url = URI.create("https://maven.fabricmc.net")
        }
    }

    override fun addIncludeToModJson(json: JsonObject, dep: Dependency, path: String) {
        var jars = json.get("jars")?.asJsonArray
        if (jars == null) {
            jars = JsonArray()
            json.add("jars", jars)
        }
        jars.add(JsonObject().apply {
            addProperty("file", path)
        })
    }

    override fun applyLaunches() {
        super.applyLaunches()
        //TODO: figure out datagen
    }

    override fun applyClientRunTransform(config: RunConfig) {
        config.mainClass = clientMainClass ?: config.mainClass
        config.jvmArgs += listOf(
            "-Dfabric.development=true",
            "-Dfabric.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.CLIENT)}\""
        )
    }

    override fun applyServerRunTransform(config: RunConfig) {
        config.mainClass = serverMainClass ?: config.mainClass
        config.jvmArgs += listOf(
            "-Dfabric.development=true",
            "-Dfabric.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.SERVER)}\""
        )
    }
}