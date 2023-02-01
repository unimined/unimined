package xyz.wagyourtail.unimined.minecraft.patch.fabric

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.run.RunConfig
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

    override fun applyClientRunConfig(tasks: TaskContainer, action: (RunConfig) -> Unit) {
        provider.provideVanillaRunClientTask(tasks) { task ->
            clientMainClass?.let { task.mainClass = it }
            task.jvmArgs += listOf(
                "-loader.development=true",
                "-loader.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.CLIENT)}\""
            )
            action(task)
        }
    }

    override fun applyServerRunConfig(tasks: TaskContainer, action: (RunConfig) -> Unit) {
        provider.provideVanillaRunServerTask(tasks) { task ->
            serverMainClass?.let { task.mainClass = it }
            task.jvmArgs += listOf(
                "-Dloader.development=true",
                "-Dloader.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.SERVER)}\""
            )
            action(task)
        }
    }


}