package xyz.wagyourtail.unimined

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.gradle.GradleEvents
import xyz.wagyourtail.unimined.providers.MappingsProviderImpl
import xyz.wagyourtail.unimined.providers.MinecraftProviderImpl
import xyz.wagyourtail.unimined.providers.mod.ModProviderImpl
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Suppress("LeakingThis")
abstract class UniminedExtensionImpl(val project: Project) : UniminedExtension() {
    val events = GradleEvents(project)

    val minecraftProvider: MinecraftProviderImpl = project.extensions.create(
        "minecraft",
        MinecraftProviderImpl::class.java,
        project,
        this
    )

    val mappingsProvider: MappingsProviderImpl = project.extensions.create(
        "mappings",
        MappingsProviderImpl::class.java,
        project,
        this
    )

    val modProvider = ModProviderImpl(project, this)

    fun getGlobalCache(): Path {
        return if (useGlobalCache.get()) {
            project.gradle.gradleUserHomeDir.toPath().resolve("caches").resolve("unimined").createDirectories()
        } else {
            getLocalCache().resolve("fakeglobal").createDirectories()
        }
    }

    fun getLocalCache(): Path {
        return project.rootProject.buildDir.toPath().resolve("unimined").createDirectories()
    }
}