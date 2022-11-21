package xyz.wagyourtail.unimined.providers

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.util.XMLBuilder
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.relativeTo

data class RunConfig(
    val project: Project,
    val taskName: String,
    var description: String,
    val commonClasspath: SourceSet,
    val launchClasspath: SourceSet,
    var mainClass: String,
    val args: MutableList<String>,
    val jvmArgs: MutableList<String>,
    val workingDir: File,
    val env: MutableMap<String, String>,
    val assetsDir: Path,
    val runFirst: List<Task>? = null
) {

    fun createIdeaRunConfig() {
        val file = project.projectDir.resolve(".idea").resolve("runConfigurations").resolve("$taskName.xml")

        val configuration = XMLBuilder("configuration").addStringOption("default", "false")
            .addStringOption("name", description)
            .addStringOption("type", "Application")
            .addStringOption("factoryName", "Application")
            .append(
//                        XMLBuilder("option").addStringOption("name", "ALTERNATIVE_JRE_PATH")
//                            .addStringOption("value", javaVersion.toString()),
                XMLBuilder("option").addStringOption("name", "ALTERNATIVE_JRE_PATH_ENABLED")
                    .addStringOption("value", "true"),
                XMLBuilder("envs").append(
                    *env.map { (key, value) ->
                        XMLBuilder("env").addStringOption("name", key).addStringOption("value", value)
                    }.toTypedArray()
                ),
                XMLBuilder("option").addStringOption("name", "MAIN_CLASS_NAME")
                    .addStringOption("value", mainClass),
                XMLBuilder("module").addStringOption("name", "${project.name}.${launchClasspath.name}"),
                XMLBuilder("option").addStringOption("name", "WORKING_DIRECTORY")
                    .addStringOption(
                        "value",
                        "\$PROJECT_DIR\$/${
                            workingDir.toPath()
                                .relativeTo(project.rootProject.projectDir.toPath())
                        }"
                    ),
                XMLBuilder("option").addStringOption("name", "VM_PARAMETERS")
                    .addStringOption(
                        "value",
                        jvmArgs.joinToString(" ") { if (it.contains(" ")) "&quot;$it&quot;" else it }),
                XMLBuilder("option").addStringOption("name", "PROGRAM_PARAMETERS")
                    .addStringOption("value", args.joinToString(" ")),
                XMLBuilder("method").addStringOption("v", "2").append(
                    XMLBuilder("option").addStringOption("name", "Make").addStringOption("enabled", "true")
                )
            )

        if (!runFirst.isNullOrEmpty()) {
            configuration.append(
                XMLBuilder("method").addStringOption("v", "2").append(
                    XMLBuilder("option").addStringOption("name", "Before launch:")
                        .addStringOption("name", "Gradle.BeforeRunTask")
                        .addStringOption("enabled", "true")
                        .addStringOption("tasks", runFirst.joinToString(" ") { it.name })
                        .addStringOption("externalProjectPath", "\$PROJECT_DIR\$")
                        .addStringOption("vmOptions", "")
                        .addStringOption("scriptParameters", "")
                )
            )
        }

        file.parentFile.mkdirs()
        file.writeText(
            XMLBuilder("component").addStringOption("name", "ProjectRunConfigurationManager").append(
                configuration
            ).toString(),
            StandardCharsets.UTF_8
        )
    }

    fun createGradleTask(tasks: TaskContainer, group: String): Task {
        return tasks.create(taskName, JavaExec::class.java) {
            it.group = group
            it.description = description
            it.mainClass.set(mainClass)
            it.workingDir = workingDir
            it.environment.putAll(env)
            workingDir.mkdirs()

            it.classpath = launchClasspath.runtimeClasspath

            it.args = args
            it.jvmArgs = jvmArgs

            if (!runFirst.isNullOrEmpty()) {
                for (task in runFirst) {
                    it.dependsOn(task)
                }
            }
        }
    }
}