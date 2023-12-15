package xyz.wagyourtail.unimined.internal.mapping.task

import org.gradle.api.Project
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.task.ExportMappingsTask
import xyz.wagyourtail.unimined.api.task.MigrateMappingsTask
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.mapping.MappingsProvider
import xyz.wagyourtail.unimined.util.deleteRecursively
import xyz.wagyourtail.unimined.util.sourceSets
import xyz.wagyourtail.unimined.util.withSourceSet
import java.io.File
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.io.path.*

abstract class MigrateMappingsTaskImpl @Inject constructor(@get:Internal val sourceSet: SourceSet) : MigrateMappingsTask() {

    @get:Internal
    val migrateSourceSet = project.sourceSets.create("migrateMappings".withSourceSet(sourceSet))

    init {
        outputDir.convention(project.projectDir.resolve("src").resolve(migrateSourceSet.name))
    }

    override fun target(config: MinecraftConfig.() -> Unit) {
        project.unimined.minecraft(migrateSourceSet, false, config)
    }

    @OptIn(ExperimentalPathApi::class)
    @TaskAction
    fun run() {
        val commonNs = commonNamespace.get()
        val inputMinecraft = project.unimined.minecrafts.map[sourceSet]!!

        //step 1, remap input to common namespace
        val inputDevNs = inputMinecraft.mappings.devNamespace
        val inputDevFNs = inputMinecraft.mappings.devFallbackNamespace
        val inputCommonNs = inputMinecraft.mappings.getNamespace(commonNs)

        val inputPath = inputMinecraft.mappings.getRemapPath(
            inputDevNs,
            inputDevFNs,
            inputCommonNs,
            inputCommonNs
        )

        val inputCommonPaths = if (inputPath.isEmpty()) {
            project.logger.lifecycle("[Unimined/MigrateMappings ${this.path}] detected empty remap path, jumping to after remap tasks")
            sourceSet.allSource.sourceDirectories.files
        } else {
            var prevTarget = sourceSet.allSource.sourceDirectories.files
            var prevNamespace = inputDevNs
            var prevPrevNamespace = inputDevFNs
            for (i in inputPath.indices) {
                val step = inputPath[i]
                project.logger.info("[Unimined/MigrateMappings ${this.path}]    $step")
                val nextTarget = temporaryDir.toPath().resolve("input-remapped-${step.name}").createDirectories()
                if (nextTarget.exists()) nextTarget.deleteRecursively()
                val mcNamespace = prevNamespace
                val mcFallbackNamespace = prevPrevNamespace

                val mc = inputMinecraft.getMinecraft(
                    mcNamespace,
                    mcFallbackNamespace
                )
                prevTarget = doRemap(inputMinecraft, prevTarget, mc, prevNamespace, step, nextTarget.toFile())
                prevPrevNamespace = prevNamespace
                prevNamespace = step
            }

            prevTarget
        }

        // step2, remap common to target
        val outputMinecraft = project.unimined.minecrafts.map[migrateSourceSet]!!

        val outputCommonNs = outputMinecraft.mappings.getNamespace(commonNs)
        val outputDevNs = outputMinecraft.mappings.devNamespace
        val outputDevFNs = outputMinecraft.mappings.devFallbackNamespace

        val outputPath = outputMinecraft.mappings.getRemapPath(
            outputCommonNs,
            outputCommonNs,
            outputDevFNs,
            outputDevNs
        )

        var prevTarget = inputCommonPaths
        var prevNamespace = outputCommonNs
        var prevPrevNamespace = outputCommonNs
        for (i in outputPath.indices) {
            val step = outputPath[i]
            project.logger.info("[Unimined/MigrateMappings ${this.path}]    $step")
            val nextTarget = temporaryDir.toPath().resolve("output-remapped-${step.name}").createDirectories()
            if (nextTarget.exists()) nextTarget.deleteRecursively()
            val mcNamespace = prevNamespace
            val mcFallbackNamespace = prevPrevNamespace

            val mc = outputMinecraft.getMinecraft(
                mcNamespace,
                mcFallbackNamespace
            )
            prevTarget = doRemap(outputMinecraft, prevTarget, mc, prevNamespace, step, nextTarget.toFile())
            prevPrevNamespace = prevNamespace
            prevNamespace = step
        }

        // step3, copy to output dir
        for (sourceDir in prevTarget) {
            sourceDir.toPath().copyToRecursively(
                outputDir.get().toPath().resolve(sourceDir.name).createDirectories(),
                followLinks = false
            )
        }

        // copy non-java/kotlin files directly
        for (sourceDir in sourceSet.allSource.sourceDirectories.files) {
            sourceDir.toPath().walk().filter { it.isRegularFile() && !it.name.endsWith(".java") && !it.name.endsWith(".kt") }.forEach {
                it.copyTo(outputDir.get().toPath().resolve(sourceDir.name).resolve(it.relativeTo(sourceDir.toPath())).createDirectories(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

    }

    fun doRemap(provider: MinecraftConfig, sources: Set<File>, minecraft: Path, source: MappingNamespaceTree.Namespace, target: MappingNamespaceTree.Namespace, outputDir: File): Set<File> {
        // export mappings to temp file
        val mappingFile = temporaryDir.toPath().resolve("mappings").createDirectories().resolve("${provider.sourceSet.name}-${source.name}-${target.name}.srg")

        // export mappings
        val export = ExportMappingsTaskImpl.ExportImpl(provider.mappings as MappingsProvider).apply {
            location = mappingFile.toFile()
            type = ExportMappingsTask.MappingExportTypes.SRG
            sourceNamespace = source
            targetNamespace = setOf(target)
        }
        export.validate()
        export.exportFunc((provider.mappings as MappingsProvider).mappingTree)

        val roots = sources.filter { it.exists() }.map { sourceDir ->
            sourceDir.toPath() to outputDir.resolve(sourceDir.name).toPath().createDirectories()
        }

        // run remap
        project.javaexec { spec ->
            spec.classpath(project.configurations.detachedConfiguration(project.dependencies.create(remapDependency.get())))
            spec.mainClass.set("com.replaymod.gradle.remap.Transformer")
            spec.args = listOf(
                mappingFile.toFile().absolutePath,
                (sourceSet.compileClasspath.files.map { it.toPath() }
                    .filter { !provider.isMinecraftJar(it) }
                    .filter { it.exists() } + listOf(minecraft))
                    .joinToString(File.pathSeparator) { it.absolutePathString() },
                roots.map { it.first }.joinToString(File.pathSeparator) { it.absolutePathString() },
                roots.map { it.second }.joinToString(File.pathSeparator) { it.absolutePathString() }
            )
            project.logger.info("[Unimined/MigrateMappings ${this.path}]    ${spec.args!!.joinToString(" ")}")
        }.rethrowFailure().assertNormalExitValue()

        return roots.map { it.second.toFile() }.toSet()
    }

}