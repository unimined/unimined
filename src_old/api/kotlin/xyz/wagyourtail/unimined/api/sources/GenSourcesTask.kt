package xyz.wagyourtail.unimined.api.sources

import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.jetbrains.annotations.ApiStatus
import java.io.File

abstract class GenSourcesTask(): ConventionTask() {
    @get:Input
    @get:Optional
    @get:ApiStatus.Internal
    abstract val decompiler: Property<String>

    @get:Input
    @get:Optional
    val args: MutableList<String> = mutableListOf(
        "-dcl=1",
        "-jrt=1",
        "-e={configurations.minecraftLibraries}",
        "{inputJar}",
        "{outputJar}"
    )

    @get:Input
    @get:Optional
    @get:ApiStatus.Internal
    abstract val decompileMods: Property<Boolean>

    private val jar: File by lazy {
        project.configurations.detachedConfiguration(
            project.dependencies.create(decompiler.get())
        ).resolve().first { it.extension == "jar" }
    }

    init {
        decompiler.convention("org.quiltmc:quiltflower:1.8.1")
        decompileMods.convention(false)
    }
}