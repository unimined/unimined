package xyz.wagyourtail.unimined

import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter
import org.gradle.api.Plugin
import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import java.nio.file.Path

class UniminedPlugin : Plugin<Project> {
    companion object UniminedPlugin {
        fun getOptions(project: Project) : UniminedExtension {
            return project.extensions.getByType(UniminedExtension::class.java)
        }

        fun getGlobalCache(project: Project): Path {
            return project.gradle.gradleUserHomeDir.toPath().resolve("caches").resolve("unimined").maybeCreate()
        }

        fun getLocalCache(project: Project): Path {
            return project.buildDir.toPath().resolve("unimined").maybeCreate()
        }
    }

    override fun apply(project: Project) {

        project.apply(mapOf(
            "plugin" to "java-library",
//            "plugin" to "idea"
        ))

        val ext = project.extensions.create("unimined", UniminedExtension::class.java)

        // init mc provider
        val mcProvider = MinecraftProvider.getMinecraftProvider(project)
        GradleRepositoryAdapter.add(project.repositories, "minecraft-transformer", getGlobalCache(project).toFile(), mcProvider.repo)
    }

}