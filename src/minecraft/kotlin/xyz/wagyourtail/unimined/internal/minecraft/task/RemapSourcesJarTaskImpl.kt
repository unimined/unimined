package xyz.wagyourtail.unimined.internal.minecraft.task

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.capitalized
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.inject.Inject
import kotlin.io.path.*

abstract class RemapSourcesJarTaskImpl @Inject constructor(provider: MinecraftConfig): RemapJarTaskImpl(provider) {
    @OptIn(ExperimentalPathApi::class)
    override fun remapToInternal(
        from: Path,
        target: Path,
        fromNs: MappingNamespaceTree.Namespace,
        toNs: MappingNamespaceTree.Namespace,
        classpathList: Array<Path>
    ) {
        val inputDir = temporaryDir.toPath().resolve("input").apply {
            project.copy {
                it.from(project.zipTree(inputFile.get().asFile))
                it.into(this)
            }
        }

        val outputDir = temporaryDir.toPath().resolve("output")

        val remapper = provider.sourceProvider.sourceRemapper

        fun Path.isSourceFile() = isRegularFile() && (name.endsWith(".java") || name.endsWith(".kt"))

        remapper.remap(
            inputDir.walk().filter { it.isSourceFile() }.associateWith {
                outputDir.resolve(inputDir.relativize(it))
            },
            project.files(classpathList),
            fromNs,
            fromNs,
            toNs,
            toNs,
            specConfig = { standardOutput = temporaryDir.resolve("remap.log").outputStream() }
        )

        // copy non-java/kotlin files directly
        inputDir.walk().filter { it.isRegularFile() && !it.isSourceFile() }.forEach {
            val relative = inputDir.relativize(it)
            val output = outputDir.resolve(relative)
            if (!output.exists()) {
                it.copyTo(output.apply { parent.createDirectories() })
            }
        }

        JarOutputStream(target.toFile().outputStream()).use { o ->
            outputDir.walk().forEach {
                val relative = outputDir.relativize(it)
                o.putNextEntry(JarEntry(relative.toString().replace('\\', '/')))
                it.inputStream().copyTo(o)
            }
        }
    }

    companion object {
        fun setup(sourceSet: SourceSet, project: Project) {
            val sourcesJarTaskName = sourceSet.sourcesJarTaskName
            val sourcesJarTask = project.tasks.findByName(sourcesJarTaskName)
            if (sourcesJarTask == null) {
                project.logger.warn("[Unimined/RemapSourcesJar] $sourcesJarTaskName task not found, not remapping sources")
                return
            }

            if (sourcesJarTask !is Jar) {
                project.logger.warn("[Unimined/RemapSourcesJar] $sourcesJarTaskName task is not a Jar task, not remapping it")
                return
            }

            val remapTaskName = "remap${sourcesJarTaskName.capitalized()}"
            val remapTask = project.tasks.register(remapTaskName, RemapSourcesJarTaskImpl::class.java, project.unimined.minecrafts[sourceSet])
            remapTask.configure {
                it.inputFile.set(sourcesJarTask.archiveFile)
                it.dependsOn(sourcesJarTask)

                it.description = "Remaps the sources jar for ${sourceSet.name}"
                it.group = "unimined"

                val oldClassifier = sourcesJarTask.archiveClassifier.get()
                it.archiveClassifier.set(oldClassifier)
                sourcesJarTask.archiveClassifier.set("dev-$oldClassifier")
            }
        }
    }
}