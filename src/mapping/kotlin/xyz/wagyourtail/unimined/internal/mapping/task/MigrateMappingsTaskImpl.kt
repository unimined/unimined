package xyz.wagyourtail.unimined.internal.mapping.task

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.mapping.task.ExportMappingsTask
import xyz.wagyourtail.unimined.api.source.task.MigrateMappingsTask
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
    val migrateSourceSet: SourceSet = project.sourceSets.create("migrateMappings".withSourceSet(sourceSet))

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

        val inputCommon = sourceSet.allSource.sourceDirectories.files.associate {
            it.toPath() to temporaryDir.toPath().resolve("input-remapped-${it.name}").createDirectories()
        }
        inputMinecraft.sourceProvider.sourceRemapper.remap(
            inputCommon,
            sourceSet.compileClasspath,
            inputDevNs,
            inputDevFNs,
            inputCommonNs,
            inputCommonNs
        )

        // step2, remap common to target
        val outputMinecraft = project.unimined.minecrafts.map[migrateSourceSet]!!

        val outputCommonNs = outputMinecraft.mappings.getNamespace(commonNs)
        val outputDevNs = outputMinecraft.mappings.devNamespace
        val outputDevFNs = outputMinecraft.mappings.devFallbackNamespace

        val commonOutput = inputCommon.values.associateWith {
            outputDir.get().toPath().resolve(it.fileName).createDirectories()
        }

        outputMinecraft.sourceProvider.sourceRemapper.remap(
            commonOutput,
            sourceSet.compileClasspath,
            outputCommonNs,
            outputCommonNs,
            outputDevFNs,
            outputDevNs
        )

        // copy non-java/kotlin files directly
        for (sourceDir in sourceSet.allSource.sourceDirectories.files) {
            sourceDir.toPath().walk().filter { it.isRegularFile() && !it.name.endsWith(".java") && !it.name.endsWith(".kt") }.forEach {
                it.copyTo(outputDir.get().toPath().resolve(sourceDir.name).resolve(it.relativeTo(sourceDir.toPath())).createDirectories(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

    }
}