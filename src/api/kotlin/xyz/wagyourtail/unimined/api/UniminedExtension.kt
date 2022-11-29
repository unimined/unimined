package xyz.wagyourtail.unimined.api

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mappings.MappingsProvider
import xyz.wagyourtail.unimined.api.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.api.mod.ModProvider
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * The main extension for Unimined.
 * @property project The project this extension is attached to.
 * @since 0.1.0
 *
 * usage:
 * ```groovy
 * unimined {
 *     useGlobalCache = false
 * }
 * ```
 *
 */
abstract class UniminedExtension(val project: Project) {
    @ApiStatus.Internal
    val events = GradleEvents(project)

    /**
     * should the global gradle cache be used, otherwise put everything in build/unimined
     * @since 0.1.0
     */
    abstract val useGlobalCache: Property<Boolean>

    @get:ApiStatus.Internal
    abstract val minecraftProvider: MinecraftProvider<*,*>

    @get:ApiStatus.Internal
    abstract val mappingsProvider: MappingsProvider

    @get:ApiStatus.Internal
    abstract val modProvider: ModProvider

    @ApiStatus.Internal
    fun getLocalCache(): Path {
        return project.rootProject.buildDir.toPath().resolve("unimined").createDirectories()
    }

    @ApiStatus.Internal
    fun getGlobalCache(): Path {
        return if (useGlobalCache.get()) {
            project.gradle.gradleUserHomeDir.toPath().resolve("caches").resolve("unimined").createDirectories()
        } else {
            getLocalCache().resolve("fakeglobal").createDirectories()
        }
    }

    init {
        @Suppress("LeakingThis")
        useGlobalCache.convention(true).finalizeValueOnRead()
    }

}