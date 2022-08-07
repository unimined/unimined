package xyz.wagyourtail.unimined.providers.patch.remap

import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.UniminedPlugin
import xyz.wagyourtail.unimined.maybeCreate
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

class ModRemapper(
    val project: Project,
    val mcRemapper: MinecraftRemapper,
    val remapFrom: String,
    val fallbackRemapFrom: String,
    val remapTo: String
) {

    private val configurations = mutableSetOf<Configuration>()

    val modCompileOnly: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modCompileOnly")
            .apply {
                extendsFrom(project.configurations.getByName("compileOnly"))
            })

    val modCompileOnlyApi: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modCompileOnlyApi")
            .apply {
                extendsFrom(project.configurations.getByName("compileOnlyApi"))
            })

    val modRuntimeOnly: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modRuntimeOnly")
            .apply {
                extendsFrom(project.configurations.getByName("runtimeOnly"))
            })

    val localRuntime: Configuration = project.configurations.maybeCreate("localRuntime").apply {
        extendsFrom(project.configurations.getByName("runtimeOnly"))
    }

    val modLocalRuntime: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modLocalRuntime")
            .apply {
                extendsFrom(project.configurations.getByName("localRuntime"))
            })

    val modApi: Configuration = registerConfiguration(project.configurations.maybeCreate("modApi").apply {
        extendsFrom(project.configurations.getByName("api"))
    })

    val modImplementation: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modImplementation")
            .apply {
                extendsFrom(project.configurations.getByName("implementation"))
            })

    private val internalModRemapperConfiguration = project.configurations.maybeCreate("internalModRemapper")

    val sourceSet = project.extensions.getByType(SourceSetContainer::class.java)

    init {
        sourceSet.findByName("main")?.apply {
            compileClasspath += modCompileOnly + modCompileOnlyApi + modApi + modImplementation
            runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation + modApi
        }
        sourceSet.findByName("client")?.apply {
            compileClasspath += modCompileOnly + modCompileOnlyApi + modApi + modImplementation
            runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation + modApi
        }
        sourceSet.findByName("server")?.apply {
            compileClasspath += modCompileOnly + modCompileOnlyApi + modApi + modImplementation
            runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation + modApi
        }

    }

    private fun registerConfiguration(configuration: Configuration): Configuration {
        configurations += configuration
        return configuration
    }

    fun init(preTransformMinecraft: Path) {
        configurations.forEach {
            preTransform(it)
        }
        tinyRemapper = TinyRemapper.newRemapper().withMappings(mcRemapper.getMappingProvider(remapFrom, remapTo))
            .renameInvalidLocals(true)
            .inferNameFromSameLvIndex(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .checkPackageAccess(true)
            .fixPackageAccess(true)
            .rebuildSourceFilenames(true)
            .extension(MixinExtension())
            .build()
        tinyRemapper.readInputs(preTransformMinecraft)
        configurations.forEach {
            transform(it)
        }
        configurations.forEach {
            postTransform(it)
        }
        tinyRemapper.finish()
    }

    val dependencyMap = mutableMapOf<Configuration, MutableSet<Dependency>>()

    fun preTransform(configuration: Configuration) {
        configuration.dependencies.forEach {
            internalModRemapperConfiguration.dependencies.add(it)
            dependencyMap.computeIfAbsent(configuration) { mutableSetOf() } += (it)
        }
        configuration.dependencies.clear()
    }

    fun transform(configuration: Configuration) {
        dependencyMap[configuration]?.forEach {
            transformMod(it)
        }
    }

    fun postTransform(configuration: Configuration) {
        dependencyMap[configuration]?.forEach {
            configuration.dependencies.add(
                project.dependencies.create(
                    project.files(getOutputs(it))
                )
            )
        }
    }

    fun modTransformFolder(): Path {
        return UniminedPlugin.getLocalCache(project).resolve("modTransform").maybeCreate()

    }

    lateinit var tinyRemapper: TinyRemapper
    val outputMap = mutableMapOf<Dependency, Pair<Set<File>, InputTag>>()

    fun transformMod(dependency: Dependency) {
        val files = internalModRemapperConfiguration.files(dependency)
        val targetTag = tinyRemapper.createInputTag()
        for (file in files) {
            if (file.extension == "jar") {
                tinyRemapper.readInputs(targetTag, file.toPath())
            }
        }
        outputMap[dependency] = Pair(files, targetTag)
    }

    fun getOutputs(dependency: Dependency): Set<File> {
        val (inputs, tag) = outputMap[dependency] ?: return emptySet()
        val outputs = mutableSetOf<File>()
        for (file in inputs) {
            if (file.extension == "jar") {
                val target = modTransformFolder()
                    .resolve("${file.nameWithoutExtension}-mapped-${mcRemapper.combinedNames}-${mcRemapper.combinedVers}.${file.extension}")
                if (target.exists()) {
                    outputs += target.toFile()
                    continue
                }
                OutputConsumerPath.Builder(target).build().use {
                    it.addNonClassFiles(file.toPath(), NonClassCopyMode.FIX_META_INF, tinyRemapper)
                    tinyRemapper.apply(it, tag)
                }
            } else {
                outputs += file
            }
        }
        return outputs
    }

}
