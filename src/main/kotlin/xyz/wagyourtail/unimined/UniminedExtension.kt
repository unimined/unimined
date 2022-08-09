package xyz.wagyourtail.unimined

import org.gradle.api.Project
import java.nio.file.Path

abstract class UniminedExtension(val project: Project) {
    val events = GradleEvents(project)

    fun getGlobalCache(): Path {
        return project.gradle.gradleUserHomeDir.toPath().resolve("caches").resolve("unimined").maybeCreate()
    }

    fun getLocalCache(): Path {
        return project.buildDir.toPath().resolve("unimined").maybeCreate()
    }
}