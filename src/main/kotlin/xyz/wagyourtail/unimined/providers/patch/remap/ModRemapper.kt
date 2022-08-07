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
import xyz.wagyourtail.unimined.maybeCreate
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

class ModRemapper(
    val project: Project,
    val mcRemapper: MinecraftRemapper
) {

    private val configurations = mutableSetOf<Configuration>()

    val modCompileOnly: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modCompileOnly")
            .apply {
                extendsFrom(project.configurations.getByName("compileOnly"))
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

    val modImplementation: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modImplementation")
            .apply {
                extendsFrom(project.configurations.getByName("implementation"))
            })

    private val internalModRemapperConfiguration = project.configurations.maybeCreate("internalModRemapper")

    private val sourceSet: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)

    init {
        sourceSet.findByName("main")?.apply {
            compileClasspath += modCompileOnly + modImplementation
            runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
        }
        sourceSet.findByName("client")?.apply {
            compileClasspath += modCompileOnly + modImplementation
            runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
        }
        sourceSet.findByName("server")?.apply {
            compileClasspath += modCompileOnly + modImplementation
            runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
        }

    }

    private fun registerConfiguration(configuration: Configuration): Configuration {
        configurations += configuration
        return configuration
    }

    fun remap() {
        configurations.forEach {
            preTransform(it)
        }
        tinyRemapper = TinyRemapper.newRemapper().withMappings(mcRemapper.getMappingProvider(mcRemapper.fallbackTarget, mcRemapper.fallbackTarget, mcRemapper.provider.targetNamespace.get()))
            .renameInvalidLocals(true)
            .inferNameFromSameLvIndex(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .checkPackageAccess(true)
            .fixPackageAccess(true)
            .rebuildSourceFilenames(true)
            .extension(MixinExtension())
            .build()
        val mc = mcRemapper.provider.getMinecraftCombinedWithMapping(mcRemapper.fallbackTarget)
        project.logger.warn(mc.toString())
        tinyRemapper.readClassPathAsync(mc)
        tinyRemapper.readClassPathAsync(*mcRemapper.provider.mcLibraries.resolve().map { it.toPath() }.toTypedArray())
        configurations.forEach {
            transform(it)
        }
        configurations.forEach {
            postTransform(it)
        }
        tinyRemapper.finish()
    }

    private val dependencyMap = mutableMapOf<Configuration, MutableSet<Dependency>>()

    private fun preTransform(configuration: Configuration) {
        configuration.dependencies.forEach {
            internalModRemapperConfiguration.dependencies.add(it)
            dependencyMap.computeIfAbsent(configuration) { mutableSetOf() } += (it)
        }
        configuration.dependencies.clear()
    }

    private fun transform(configuration: Configuration) {
        dependencyMap[configuration]?.forEach {
            transformMod(it)
        }
    }

    private fun postTransform(configuration: Configuration) {
        dependencyMap[configuration]?.forEach {
            configuration.dependencies.add(
                project.dependencies.create(
                    project.files(getOutputs(it))
                )
            )
        }
    }

    private fun modTransformFolder(): Path {
        return mcRemapper.provider.parent.getLocalCache().resolve("modTransform").maybeCreate()

    }

    private lateinit var tinyRemapper: TinyRemapper
    private val outputMap = mutableMapOf<File, InputTag>()

    private fun transformMod(dependency: Dependency) {
        val files = internalModRemapperConfiguration.files(dependency)
        for (file in files) {
            if (file.extension == "jar") {
                val targetTag = tinyRemapper.createInputTag()
                tinyRemapper.readInputs(targetTag, file.toPath())
                outputMap[file] = targetTag
            }
        }
    }

    private fun getOutputs(dependency: Dependency): Set<File> {
        val mappingsDependecies = (mcRemapper.mappings.dependencies as Set<Dependency>).sortedBy { "${it.name}-${it.version}" }
        val combinedNames = mappingsDependecies.joinToString("+") { it.name + "-" + it.version }

        val outputs = mutableSetOf<File>()
        for (file in internalModRemapperConfiguration.files(dependency)) {
            if (file.extension == "jar") {
                val target = modTransformFolder()
                    .resolve("${file.nameWithoutExtension}-mapped-${combinedNames}-${mcRemapper.provider.targetNamespace.get()}.${file.extension}")
                if (target.exists()) {
                    outputs += target.toFile()
                    continue
                }
                OutputConsumerPath.Builder(target).build().use {
                    it.addNonClassFiles(file.toPath(), NonClassCopyMode.FIX_META_INF, tinyRemapper)
                    tinyRemapper.apply(it, outputMap[file])
                }
            } else {
                outputs += file
            }
        }
        return outputs
    }

}
