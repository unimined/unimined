package xyz.wagyourtail.unimined

import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.mappings.MappingsProvider
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.mod.ModProvider
import java.nio.file.Path

@Suppress("LeakingThis")
abstract class UniminedExtension(val project: Project) {
    val events = GradleEvents(project)

    val minecraftProvider = project.extensions.create("minecraft", MinecraftProvider::class.java, project, this)
    val mappingsProvider = project.extensions.create("mappings", MappingsProvider::class.java, project, this)
    val modProvider = ModProvider(project, this)

    fun getGlobalCache(): Path {
        return project.gradle.gradleUserHomeDir.toPath().resolve("caches").resolve("unimined").maybeCreate()
    }

    fun getLocalCache(): Path {
        return project.buildDir.toPath().resolve("unimined").maybeCreate()
    }
}