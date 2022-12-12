package xyz.wagyourtail.unimined.minecraft.patch.fabric

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.fabric.FabricApiExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.run.RunConfig
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import java.net.URI

class FabricMinecraftTransformer(
    project: Project,
    provider: MinecraftProviderImpl
) : FabricLikeMinecraftTransformer(
    project,
    provider,
    Constants.FABRIC_PROVIDER,
    "fabric.mod.json",
    "accessWidener"
) {

    init {
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

    override fun applyClientRunConfig(tasks: TaskContainer, action: (RunConfig) -> Unit) {
        provider.provideVanillaRunClientTask(tasks) { task ->
            clientMainClass?.let { task.mainClass = it }
            task.jvmArgs += listOf(
                "-Dfabric.development=true",
                "-Dfabric.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.CLIENT)}\""
            )
            action(task)
        }
    }

    override fun applyServerRunConfig(tasks: TaskContainer, action: (RunConfig) -> Unit) {
        provider.provideVanillaRunServerTask(tasks) { task ->
            serverMainClass?.let { task.mainClass = it }
            task.jvmArgs += listOf(
                "-Dfabric.development=true",
                "-Dfabric.remapClasspathFile=\"${getIntermediaryClassPath(EnvType.SERVER)}\""
            )
            action(task)
        }
    }
}