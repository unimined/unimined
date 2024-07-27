package xyz.wagyourtail.unimined.internal.mapping.task

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.source.task.MigrateMappingsTask
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.sourceSets
import xyz.wagyourtail.unimined.util.withSourceSet
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
        val inputMinecraft = project.unimined.minecrafts[sourceSet]!!
        val outputMinecraft = project.unimined.minecrafts[migrateSourceSet]!!

        val inputCommon = sourceSet.allSource.sourceDirectories.files.associate {
            it.toPath() to temporaryDir.toPath().resolve("input-remapped-${it.name}").createDirectories()
        }

        //step 1, remap input to common namespace
        val inputDevNs = inputMinecraft.mappings.devNamespace
        val outputDevNs = outputMinecraft.mappings.devNamespace


        outputMinecraft.sourceProvider.sourceRemapper.remap(
            inputCommon,
            sourceSet.compileClasspath,
            inputDevNs,
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