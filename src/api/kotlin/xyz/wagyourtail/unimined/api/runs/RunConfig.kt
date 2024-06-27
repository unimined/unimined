package xyz.wagyourtail.unimined.api.runs

import groovy.lang.Closure
import org.gradle.api.JavaVersion
import org.gradle.api.Task
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import xyz.wagyourtail.unimined.util.XMLBuilder
import xyz.wagyourtail.unimined.util.removeALl
import xyz.wagyourtail.unimined.util.withSourceSet
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlin.io.path.relativeTo

abstract class RunConfig @Inject constructor(
    @get:Input
    val sourceSet: SourceSet,
    @get:Internal
    val preRunTask: TaskProvider<Task>
) : JavaExec() {

    @get:Suppress("ACCIDENTAL_OVERRIDE")
    var javaVersion: JavaVersion?
        get() = super.getJavaVersion()
        set(value) {
            val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
            if (value == null) {
                javaLauncher.set(toolchains.launcherFor { })
            } else {
                javaLauncher.set(toolchains.launcherFor {
                    it.languageVersion.set(JavaLanguageVersion.of(value.majorVersion))
                })
            }
        }

    @get:Internal
    val properties: MutableMap<String, () -> String> = mutableMapOf()

    init {
        group = "unimined_runs"
        dependsOn(preRunTask)
    }

    fun setProperty(
        key: String,
        value: Closure<String>
    ) {
        properties[key] = { value.call() }
    }

    private fun <T> applyProperties(arg: T): T {
        return if (arg is String) {
            arg.replace(Regex("\\$\\{([^}]+)}")) {
                val key = it.groupValues[1]
                if (properties.containsKey(key)) {
                    properties.getValue(key).invoke()
                } else {
                    project.logger.warn("[Unimined/RunConfig ${path}]Property $key not found")
                    ""
                }
            } as T
        } else arg
    }

    private fun applyAll() {
        args = args!!.map { applyProperties(it) }
        jvmArgs = jvmArgs!!.map { applyProperties(it) }
        environment = environment.mapValues { (_, value) -> applyProperties(value) }
    }

    @TaskAction
    override fun exec() {
        applyAll()
        super.exec()
    }

    fun createIdeaRunConfig() {
        if (!this.enabled) return
        applyAll()
        val file = project.rootDir.resolve(".idea")
            .resolve("runConfigurations")
            .resolve("${if (project.path != ":") project.path.replace(":", "_") + "_" else ""}+${name.withSourceSet(sourceSet)}.xml")

        val configuration = XMLBuilder("configuration").addStringOption("default", "false")
            .addStringOption("name", buildString {
                if (project != project.rootProject) append(project.path)
                if (sourceSet.name != "main") append("+${sourceSet.name}")
                append(" ")
                append(description)
            })
            .addStringOption("type", "Application")
            .addStringOption("factoryName", "Application")

        javaLauncher.orNull?.let { launcher ->
            configuration.append(
                XMLBuilder("option").addStringOption("name", "ALTERNATIVE_JRE_PATH")
                    .addStringOption("value", launcher.metadata.installationPath.asFile.absolutePath),
                XMLBuilder("option").addStringOption("name", "ALTERNATIVE_JRE_PATH_ENABLED")
                    .addStringOption("value", "true")
            )
        }

        configuration.append(
            XMLBuilder("envs").append(
                *(environment.removeALl(System.getenv())).map { (key, value) ->
                    XMLBuilder("env").addStringOption("name", key).addStringOption("value", value.toString())
                }.toTypedArray()
            ),
            XMLBuilder("option").addStringOption("name", "MAIN_CLASS_NAME")
                .addStringOption("value", mainClass.getOrElse("")),
            XMLBuilder("module").addStringOption(
                "name",
                "${
                    if (project != project.rootProject) "${project.rootProject.name}${
                        project.path.replace(
                            ":",
                            "."
                        )
                    }" else project.name
                }.${sourceSet.name}"
            ),
            XMLBuilder("classpathModifications").append(
                *classpath.filter { !sourceSet.runtimeClasspath.contains(it) }.map {
                    XMLBuilder("entry").addStringOption("path", it.absolutePath)
                }.toTypedArray(),
                *sourceSet.runtimeClasspath.filter { !classpath.contains(it) }.map {
                    XMLBuilder("entry").addStringOption("exclude", "true").addStringOption("path", it.absolutePath)
                }.toTypedArray()
            ),
            XMLBuilder("option").addStringOption("name", "PROGRAM_PARAMETERS")
                .addStringOption("value", args?.joinToString(" ") { if (it.contains(" ")) "&quot;$it&quot;" else it } ?: ""),
            XMLBuilder("option").addStringOption("name", "VM_PARAMETERS")
                .addStringOption(
                    "value",
                    jvmArgs?.joinToString(" ") { if (it.contains(" ")) "&quot;$it&quot;" else it } ?: ""),
            XMLBuilder("option").addStringOption("name", "WORKING_DIRECTORY")
                .addStringOption(
                    "value",
                    "\$PROJECT_DIR\$/${
                        workingDir.toPath()
                            .relativeTo(project.rootProject.projectDir.toPath())
                    }"
                ),
        )

        val mv2 = XMLBuilder("method")
            .addStringOption("v", "2")
            .append(
                XMLBuilder("option").addStringOption("name", "Make").addStringOption("enabled", "true")
            )

        mv2.append(
            XMLBuilder("option")
                .addStringOption("name", "Gradle.BeforeRunTask")
                .addStringOption("enabled", "true")
                .addStringOption("tasks", preRunTask.name)
                .addStringOption(
                    "externalProjectPath",
                    "\$PROJECT_DIR\$/${
                        project.projectDir.toPath()
                            .relativeTo(project.rootProject.projectDir.toPath())
                    }"
                )
                .addStringOption("vmOptions", "")
                .addStringOption("scriptParameters", "")
        )

        configuration.append(mv2)

        file.parentFile.mkdirs()
        file.writeText(
            XMLBuilder("component").addStringOption("name", "ProjectRunConfigurationManager").append(
                configuration
            ).toString(),
            StandardCharsets.UTF_8
        )

    }

}