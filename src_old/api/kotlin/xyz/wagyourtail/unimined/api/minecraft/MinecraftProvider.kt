package xyz.wagyourtail.unimined.api.minecraft

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.launch.LauncherProvider
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.FabricLikePatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.JarModAgentPatcher
import xyz.wagyourtail.unimined.api.minecraft.patch.MergedPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.MinecraftPatcher
import xyz.wagyourtail.unimined.api.minecraft.transform.remap.MinecraftRemapper
import xyz.wagyourtail.unimined.util.LazyMutable
import java.io.File
import java.nio.file.Path

val Project.minecraft
    get() = this.extensions.getByType(MinecraftProvider::class.java)

/**
 * The main interface for interacting with minecraft.
 * @property project The project this extension is attached to.
 * @since 0.1.0
 */
@Suppress("LeakingThis")
abstract class MinecraftProvider<T: MinecraftRemapper, U: MinecraftPatcher>(val project: Project) : PatchProviders {
    @get:ApiStatus.Internal
    abstract val minecraft: MinecraftResolver

    /**
     * The class responsible for remapping minecraft.
     * @since 0.2.3
     */
    abstract val mcRemapper: T

    /**
     * The class responsible for patching minecraft.
     * please manipulate from within [jarMod], [fabric], or [forge].
     * @since 0.2.3
     */
    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    abstract var mcPatcher: U

    /**
     * allows for transforming the dev launches.
     * @since 0.4.0
     */
    abstract val launcher: LauncherProvider

    /**
     * namespace to use when merging
     * @since 0.4.2
     */
    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    var mergeNamespace: MappingNamespace = MappingNamespace.OFFICIAL

    /**
     * @since 0.4.2
     */
    fun setMergeNamespace(namespace: String) {
        mergeNamespace = MappingNamespace.getNamespace(namespace)
    }

    @Deprecated("use launcher instead", ReplaceWith("launcher"))
    val runs
        get() = launcher

    abstract val clientWorkingDirectory: Property<File>
    abstract val serverWorkingDirectory: Property<File>

    var combinedSourceSets: List<SourceSet> by LazyMutable {
        if (disableCombined.get()) {
            listOf()
        } else {
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            val sets = (sourceSets.asMap.values.toSet() - (clientSourceSets + serverSourceSets).toSet())
            val main = sourceSets.getByName("main")
            if (sets.contains(main)) {
                listOf(main) + (sets - main)
            } else {
                sets.toList()
            }
        }
    }

    var clientSourceSets: List<SourceSet> by LazyMutable {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val sets = setOfNotNull(sourceSets.findByName("client"))
        val main = sourceSets.getByName("main")
        if (sets.contains(main)) {
            listOf(main) + (sets - main)
        } else {
            sets.toList()
        }
    }

    var serverSourceSets: List<SourceSet> by LazyMutable {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val sets = setOfNotNull(sourceSets.findByName("server"))
        val main = sourceSets.getByName("main")
        if (sets.contains(main)) {
            listOf(main) + (sets - main)
        } else {
            sets.toList()
        }
    }

    var defaultEnv: EnvType by LazyMutable {
        if (disableCombined.get() || combinedSourceSets.isEmpty()) {
            if (clientSourceSets.isEmpty()) {
                EnvType.SERVER
            } else {
                EnvType.CLIENT
            }
        } else {
            EnvType.COMBINED
        }
    }

    /**
     * disables the combined mc jar
     */
    @get:ApiStatus.Experimental
    abstract val disableCombined: Property<Boolean>

    /**
     * set the mc version for server seperately from client.
     */
    abstract val serverVersionOverride: Property<String?>

    @get:ApiStatus.Internal
    abstract val mcLibraries: Configuration

    init {
        clientWorkingDirectory.convention(project.projectDir.resolve("run").resolve("client")).finalizeValueOnRead()
        serverWorkingDirectory.convention(project.projectDir.resolve("run").resolve("server")).finalizeValueOnRead()

        disableCombined.convention(false).finalizeValueOnRead()

        serverVersionOverride.convention(null as String?).finalizeValueOnRead()
    }

    /**
     * @since 0.4.10
     */
    abstract fun merged(action: (MergedPatcher) -> Unit)

    /**
     * @since 0.4.10
     */

    fun merged(
        @DelegatesTo(
            value = MergedPatcher::class,
            strategy = Closure.DELEGATE_FIRST
        ) action: Closure<*>
    ) {
        merged {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    @ApiStatus.Internal
    abstract fun getMinecraftWithMapping(
        envType: EnvType,
        namespace: MappingNamespace,
        fallbackNamespace: MappingNamespace
    ): Path


    abstract fun isMinecraftJar(path: Path): Boolean
}