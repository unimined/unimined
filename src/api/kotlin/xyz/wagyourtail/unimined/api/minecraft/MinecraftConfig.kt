package xyz.wagyourtail.unimined.api.minecraft

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.tasks.SourceSet
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.api.minecraft.patch.MergedPatcher
import xyz.wagyourtail.unimined.api.minecraft.patch.MinecraftPatcher
import xyz.wagyourtail.unimined.api.minecraft.remap.MinecraftRemapConfig
import xyz.wagyourtail.unimined.api.minecraft.resolver.MinecraftData
import xyz.wagyourtail.unimined.api.mod.ModsConfig
import xyz.wagyourtail.unimined.api.runs.RunsConfig
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.MustSet
import xyz.wagyourtail.unimined.util.sourceSets
import java.io.File
import java.nio.file.Path

/**
 * add minecraft to a sourceSet
 *
 * usage:
 * ```groovy
 * unimined.minecraft {
 *
 *     version "1.12.2"
 *
 *     // auto-set to combined on 1.3+
 *     side "combined"
 *
 *     mappings {
 *         // see MappingsConfig
 *     }
 *
 *
 *     // select mod loader, for full options, see PatchProviders
 *     /*
 *     forge {
 *         // see ForgePatcher
 *     }
 *     */
 *     /*
 *     fabric {
 *         // see FabricLikePatcher
 *     }
 *     */
 *     /*
 *     merged {
 *         // see MergedPatcher
 *     }
 *     */
 *
 *
 * }
 * ```
 *
 * @see MappingsConfig
 * @see PatchProviders
 * @since 1.0.0
 */
abstract class MinecraftConfig(val project: Project, val sourceSet: SourceSet) : PatchProviders {

    @set:ApiStatus.Internal
    var side by FinalizeOnRead(LazyMutable { if (!mcPatcher.canCombine) error("must set \"side\" for minecraft to either \"client\" or \"server\"") else EnvType.COMBINED })

    /**
     * sets the side for minecraft (client, server, combined, or datagen)
     * TODO: make datagen work properly
     */
    fun side(sideConf: String) {
        project.logger.info("setting minecraft side to $sideConf")
        side = EnvType.valueOf(sideConf.uppercase())
    }

    /**
     * the minecraft version to use
     */
    var version: String by FinalizeOnRead(MustSet())

    /**
     * should unimined add the default "remapJar" task to this sourceSet?
     */
    var defaultRemapJar: Boolean by FinalizeOnRead(true)

    /**
     * if the jar task for defaultRemapJar doesn't exist, should unimined create it?
     * if so, it will be created with `from(this.sourceSet.output, sourceSets.main.output)`
     * and it's archiveClassifier will be this.sourceSet.name
     */
    var createJarTask: Boolean by FinalizeOnRead(true)

    @set:ApiStatus.Internal
    abstract var mcPatcher: MinecraftPatcher

    abstract val mappings: MappingsConfig
    abstract val mods: ModsConfig
    abstract val runs: RunsConfig
    abstract val minecraftData: MinecraftData
    abstract val minecraftRemapper: MinecraftRemapConfig



    /**
     * @since 1.1.0
     * copy the config closure from the config for another sourceSet
     */
    fun from(sourceSet: SourceSet) {
        from(project, sourceSet)
    }

    /**
     * @since 1.1.0
     * copy the config closure from the config for another sourceSet
     */
    abstract fun from(project: Project, sourceSet: SourceSet)

    /**
     * @since 1.1.0
     * copy the config closure from the config for another sourceSet
     */
    fun from(path: String) {
        if (!path.contains(":")) {
            from(project.sourceSets.getByName(path))
            return
        }
        val project = path.substringBeforeLast(":")
        val name = path.substringAfterLast(":")
        from(this.project.project(path).sourceSets.getByName(name))
    }

    /**
     * @since 1.1.0
     */
    fun combineWith(sourceSet: SourceSet) {
        combineWith(project, sourceSet)
    }

    /**
     * @since 1.1.0
     * calls `from(project, sourceSet)` and then also adds the target to the compileClasspath and runtimeClasspath, as well as adding it to the `from` in the jar task
     * this also fixes some other things, like resources being split between the two sourceSets
     *
     * if the target isn't a unimined sourceSet, this will skip the `from` call
     */
    abstract fun combineWith(project: Project, sourceSet: SourceSet)

    /**
     * @since 1.1.0
     */
    fun combineWith(path: String) {
        if (!path.contains(":")) {
            combineWith(project, project.sourceSets.getByName(path))
            return
        }
        val project = path.substringBeforeLast(":")
        val name = path.substringAfterLast(":")
        combineWith(this.project.project(path), this.project.project(path).sourceSets.getByName(name))
    };

    /**
     * the minecraft version to use
     */
    fun version(version: String) {
        project.logger.info("setting minecraft version to $version")
        this.version = version
    }

    abstract fun mappings(action: MappingsConfig.() -> Unit)

    fun mappings(
        @DelegatesTo(value = MappingsConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mappings {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    abstract fun merged(action: MergedPatcher.() -> Unit)

    fun merged(
        @DelegatesTo(
            value = MergedPatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        merged {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun remap(task: Task) {
        remap(task) {}
    }

    fun remap(task: Task, action: RemapJarTask.() -> Unit) {
        remap(task, "remap${task.name.capitalized()}", action)
    }

    fun remap(
        task: Task,
        @DelegatesTo(value = RemapJarTask::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        remap(task) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun remap(task: Task, name: String) {
        remap(task, name) {}
    }

    abstract fun remap(task: Task, name: String, action: RemapJarTask.() -> Unit)

    fun remap(
        task: Task,
        name: String,
        @DelegatesTo(value = RemapJarTask::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        remap(task, name) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun mods(action: ModsConfig.() -> Unit) {
        mods.action()
    }

    fun mods(
        @DelegatesTo(value = ModsConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        mods {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    fun runs(action: RunsConfig.() -> Unit) {
        runs.action()
    }

    fun runs(
        @DelegatesTo(value = RunsConfig::class, strategy = Closure.DELEGATE_FIRST)
        action: Closure<*>
    ) {
        runs {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Internal
    abstract fun getMinecraft(
        namespace: MappingNamespaceTree.Namespace,
        fallbackNamespace: MappingNamespaceTree.Namespace
    ): Path

    @get:ApiStatus.Internal
    abstract val minecraftFileDev: File

    @get:ApiStatus.Internal
    abstract val mergedOfficialMinecraftFile: File?

    @get:ApiStatus.Internal
    abstract val minecraftLibraries: Configuration

    @ApiStatus.Internal
    abstract fun isMinecraftJar(path: Path): Boolean

    @get:ApiStatus.Internal
    abstract val minecraftDependency: ModuleDependency

    @get:ApiStatus.Internal
    val localCache by lazy { project.unimined.getLocalCache(sourceSet) }

    @get:ApiStatus.Internal
    abstract val combinedWithList: MutableList<Pair<Project, SourceSet>>
}