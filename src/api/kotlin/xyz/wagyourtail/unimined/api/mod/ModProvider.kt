package xyz.wagyourtail.unimined.api.mod

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.configurationcache.extensions.capitalized
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.minecraft


val Project.modProvider
    get() = extensions.getByType(ModProvider::class.java)

/**
 * The class responsible for providing mod configurations and remapping them.
 * @since 0.2.3
 */
abstract class ModProvider(val uniminedExtension: UniminedExtension) {

    /**
     * mod remapper.
     */
    abstract val modRemapper: ModRemapper

    /**
     * The mod configurations.
     * @since 0.4.0
     */
    abstract val combinedConfig: Configs
    /**
     * The mod configurations.
     * @since 0.4.0
     */
    abstract val clientConfig: Configs
    /**
     * The mod configurations.
     * @since 0.4.0
     */
    abstract val serverConfig: Configs

    data class Configs(val project: Project, val envType: EnvType, val uniminedExtension: UniminedExtension) {
        val configurations = mutableSetOf<Configuration>()
        private val envTypeName = envType.classifier?.capitalized() ?: ""

        private fun registerConfiguration(configuration: Configuration): Configuration {
            configurations += configuration
            return configuration
        }

        val modCompileOnly: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modCompileOnly$envTypeName")
                .apply {
                    extendsFrom(project.configurations.getByName("compileOnly"))
                    exclude(
                        mapOf(
                            "group" to "net.fabricmc",
                            "module" to "fabric-loader"
                        )
                    )
                })

        val modRuntimeOnly: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modRuntimeOnly$envTypeName")
                .apply {
                    extendsFrom(project.configurations.getByName("runtimeOnly"))
                    exclude(
                        mapOf(
                            "group" to "net.fabricmc",
                            "module" to "fabric-loader"
                        )
                    )
                })

        val localRuntime: Configuration = project.configurations.maybeCreate("localRuntime$envTypeName").apply {
            extendsFrom(project.configurations.getByName("runtimeOnly"))
            exclude(
                mapOf(
                    "group" to "net.fabricmc",
                    "module" to "fabric-loader"
                )
            )
        }

        val modLocalRuntime: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modLocalRuntime" + envTypeName)
                .apply {
                    extendsFrom(project.configurations.getByName("localRuntime"))
                    exclude(
                        mapOf(
                            "group" to "net.fabricmc",
                            "module" to "fabric-loader"
                        )
                    )
                })

        val modImplementation: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modImplementation" + envTypeName)
                .apply {
                    extendsFrom(project.configurations.getByName("implementation"))
                    exclude(
                        mapOf(
                            "group" to "net.fabricmc",
                            "module" to "fabric-loader"
                        )
                    )
                })


        init {
            uniminedExtension.events.register(::sourceSets)
        }

        private fun sourceSets(sourceSets: SourceSetContainer) {
            when (envType) {
                EnvType.SERVER -> {
                    for (sourceSet in project.minecraft.serverSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                }

                EnvType.CLIENT -> {
                    for (sourceSet in project.minecraft.clientSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                }

                EnvType.COMBINED -> {
                    for (sourceSet in project.minecraft.combinedSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                    for (sourceSet in project.minecraft.serverSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                    for (sourceSet in project.minecraft.clientSourceSets){
                        sourceSet.compileClasspath += modCompileOnly + modImplementation
                        sourceSet.runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                }
            }
        }
    }
}