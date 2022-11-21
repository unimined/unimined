package xyz.wagyourtail.unimined

import org.gradle.api.Project
import org.gradle.api.provider.Property
import xyz.wagyourtail.unimined.gradle.GradleEvents
import xyz.wagyourtail.unimined.providers.MinecraftProvider
import xyz.wagyourtail.unimined.providers.mappings.MappingsProvider
import xyz.wagyourtail.unimined.providers.mod.ModProvider
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Suppress("LeakingThis")
abstract class UniminedExtension(val project: Project) {
    val events = GradleEvents(project)

    val minecraftProvider: MinecraftProvider = project.extensions.create(
        "minecraft",
        MinecraftProvider::class.java,
        project,
        this
    )
    val mappingsProvider: MappingsProvider = project.extensions.create(
        "mappings",
        MappingsProvider::class.java,
        project,
        this
    )
    val modProvider = ModProvider(project, this)

    abstract val useGlobalCache: Property<Boolean>

    init {
        useGlobalCache.convention(true).finalizeValueOnRead()
    }

    fun getGlobalCache(): Path {
        return if (useGlobalCache.get()) {
            project.gradle.gradleUserHomeDir.toPath().resolve("caches").resolve("unimined").createDirectories()
        } else {
            getLocalCache().resolve("fakeglobal").createDirectories()
        }
    }

    fun getLocalCache(): Path {
        return project.buildDir.toPath().resolve("unimined").createDirectories()
    }
}