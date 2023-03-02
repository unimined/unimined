package xyz.wagyourtail.unimined.minecraft.patch.fabric

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.launch.LaunchConfig
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import java.net.URI

class QuiltMinecraftTransformer(
    project: Project,
    provider: MinecraftProviderImpl
) : FabricLikeMinecraftTransformer(
    project,
    provider,
    Constants.QUILT_PROVIDER,
    "quilt.mod.json",
    "access_widener"
) {

    override val ENVIRONMENT: String = "Lnet/fabricmc/api/Environment;"
    override val ENV_TYPE: String = "Lnet/fabricmc/api/EnvType;"

    override fun addMavens() {
        project.repositories.maven {
            it.url = URI.create("https://maven.quiltmc.org/repository/release")
        }
        project.repositories.maven {
            it.url = URI.create("https://maven.fabricmc.net")
        }
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

    override fun applyLaunches() {
        super.applyLaunches()
        //TODO: figure out datagen
    }

    override fun applyClientRunTransform(config: LaunchConfig) {
        config.mainClass = clientMainClass ?: config.mainClass
        config.jvmArgs += listOf(
            "-Dloader.development=true",
            "-Dloader.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.CLIENT)}\""
        )
    }
    override fun applyServerRunTransform(config: LaunchConfig) {
        config.mainClass = serverMainClass ?: config.mainClass
        config.jvmArgs += listOf(
            "-Dloader.development=true",
            "-Dloader.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.SERVER)}\""
        )
    }


}