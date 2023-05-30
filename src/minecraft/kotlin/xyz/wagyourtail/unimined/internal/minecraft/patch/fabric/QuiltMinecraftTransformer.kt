package xyz.wagyourtail.unimined.internal.minecraft.patch.fabric

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import java.net.URI

class QuiltMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider
): FabricLikeMinecraftTransformer(
    project,
    provider,
    "quilt",
    "quilt.mod.json",
    "access_widener"
) {

    override val ENVIRONMENT: String = "Lnet/fabricmc/api/Environment;"
    override val ENV_TYPE: String = "Lnet/fabricmc/api/EnvType;"

    override fun addIntermediaryMappings() {
        provider.mappings {
            intermediary()
        }
    }

    override fun loader(dep: Any, action: Dependency.() -> Unit) {
        fabric.dependencies.add(
            (if (dep is String && !dep.contains(":")) {
                project.dependencies.create("org.quiltmc:quilt-loader:$dep")
            } else project.dependencies.create(dep)).apply(action)
        )
    }

    override fun addMavens() {
        project.unimined.quiltMaven()
        project.unimined.fabricMaven()
    }

    override fun addIncludeToModJson(json: JsonObject, dep: Dependency, path: String) {
        val quilt_loader = json.get("quilt_loader").asJsonObject
        var jars = quilt_loader.get("jars")?.asJsonArray
        if (jars == null) {
            jars = JsonArray()
            quilt_loader.add("jars", jars)
        }

        jars.add(path)
    }

    override fun applyExtraLaunches() {
        super.applyExtraLaunches()
        if (provider.side == EnvType.DATAGEN) {
            TODO("DATAGEN not supported yet")
        }
    }

    override fun applyClientRunTransform(config: RunConfig) {
        super.applyClientRunTransform(config)
        config.jvmArgs += listOf(
            "-Dloader.development=true",
            "-Dloader.remapClasspathFile=\"${intermediaryClasspath}\""
        )
    }

    override fun applyServerRunTransform(config: RunConfig) {
        super.applyServerRunTransform(config)
        config.jvmArgs += listOf(
            "-Dloader.development=true",
            "-Dloader.remapClasspathFile=\"${intermediaryClasspath}\""
        )
    }


}