package xyz.wagyourtail.unimined.api

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import java.nio.file.Path
import kotlin.io.path.createDirectories
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.api.minecraft.patch.fabric.FabricLikeApiExtension

val Project.unimined
    get() = extensions.getByType(UniminedExtension::class.java)

/**
 * the main entrypoint.
 *
 * usage:
 * ```groovy
 *
 * unimined.useGlobalCache = false
 *
 * unimined.minecraft {
 *     // See MinecraftConfig
 * }
 *
 * // optional second config for another sourceSet
 * unimined.minecraft(sourceSets.other) {
 *     // See MinecraftConfig
 * }
 * ```
 *
 * @see MinecraftConfig
 * @since 1.0.0
 */
abstract class UniminedExtension(val project: Project) {

    var useGlobalCache: Boolean by FinalizeOnRead(true)
    var forceReload: Boolean by FinalizeOnRead(java.lang.Boolean.getBoolean("unimined.forceReload"))

    var fabricApi = project.extensions.create("fabricApi", FabricLikeApiExtension::class.java)

    private val sourceSets by lazy {
        project.extensions.getByType(SourceSetContainer::class.java)
    }

    fun minecraft(action: MinecraftConfig.() -> Unit): MinecraftConfig {
        return minecraft(sourceSets.getByName("main"), action)
    }

    /**
     * @since 1.0.0
     */
    abstract fun minecraft(sourceSet: SourceSet, action: MinecraftConfig.() -> Unit): MinecraftConfig


    /**
     * @since 1.0.0
     */
    fun minecraft(
        @DelegatesTo(value = MinecraftConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        minecraft(sourceSets.getByName("main")) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * @since 1.0.0
     */
    fun minecraft(
        sourceSet: SourceSet,
        @DelegatesTo(value = MinecraftConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        minecraft(sourceSet) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Internal
    fun getLocalCache(): Path {
        return project.rootProject.buildDir.toPath().resolve("unimined").createDirectories()
    }

    @ApiStatus.Internal
    fun getGlobalCache(): Path {
        return if (useGlobalCache) {
            project.gradle.gradleUserHomeDir.toPath().resolve("caches").resolve("unimined").createDirectories()
        } else {
            getLocalCache().resolve("fakeglobal").createDirectories()
        }
    }

    abstract val modsRemapRepo: FlatDirectoryArtifactRepository
    abstract fun forgeMaven()
    abstract fun fabricMaven()
    abstract fun legacyFabricMaven()
    abstract fun wagYourMaven(name: String)
    abstract fun mcphackersIvy()
    abstract fun quiltMaven()
    abstract fun babricMaven()
}