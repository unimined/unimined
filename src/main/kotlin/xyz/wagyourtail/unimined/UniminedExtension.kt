package xyz.wagyourtail.unimined

import org.gradle.api.Project
import xyz.wagyourtail.unimined.maybeCreate
import java.nio.file.Path

@Suppress("LeakingThis")
abstract class UniminedExtension(val project: Project) {

    fun getGlobalCache(): Path {
        return project.gradle.gradleUserHomeDir.toPath().resolve("caches").resolve("unimined").maybeCreate()
    }

    fun getLocalCache(): Path {
        return project.buildDir.toPath().resolve("unimined").maybeCreate()
    }
}