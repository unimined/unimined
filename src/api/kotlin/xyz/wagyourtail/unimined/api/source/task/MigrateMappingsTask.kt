package xyz.wagyourtail.unimined.api.source.task

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.unimined
import java.io.File

/**
 * @since 1.2.0
 */
abstract class MigrateMappingsTask : ConventionTask() {

    @get:Input
    @get:Optional
    abstract val outputDir: Property<File>

    @get:Input
    abstract val commonNamespace: Property<String>
    init {
        project.unimined.wagYourMaven("snapshots")
    }

    /**
     * set the target version/mappings to migrate to.
     */
    abstract fun target(config: MinecraftConfig.() -> Unit)

    fun target(
        @DelegatesTo(value = MinecraftConfig::class, strategy = Closure.DELEGATE_FIRST)
        config: Closure<*>
    ) {
        target {
            config.delegate = this
            config.resolveStrategy = Closure.DELEGATE_FIRST
            config.call()
        }
    }

}