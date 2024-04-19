package xyz.wagyourtail.unimined.api.runs

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import xyz.wagyourtail.unimined.api.runs.auth.AuthConfig
import xyz.wagyourtail.unimined.util.XMLBuilder
import xyz.wagyourtail.unimined.util.withSourceSet
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.path.relativeTo

/**
 * abstraction layer for minecraft run configs.
 * @since 0.2.3
 */
data class RunConfig(
    val project: Project,
    var javaVersion: JavaVersion?, // set to null to use whatever gradle/idea is using
    val name: String,
    val taskName: String,
    var description: String,
    var launchClasspath: SourceSet,
    var mainClass: String,
    val args: MutableList<String>,
    val jvmArgs: MutableList<String>,
    var workingDir: File,
    val env: MutableMap<String, String>,
    val runFirst: MutableList<Task> = mutableListOf(),
    var disabled : Boolean = false
) {

    fun createIdeaRunConfig() {
        val file = project.rootDir.resolve(".idea")
            .resolve("runConfigurations")
            .resolve("${if (project.path != ":") project.path.replace(":", "_") + "_" else ""}+${taskName.withSourceSet(launchClasspath)}.xml")

        val configuration = XMLBuilder("configuration").addStringOption("default", "false")
            .addStringOption("name", buildString {
                if (project != project.rootProject) append("${project.path}+")
                if (launchClasspath.name != "main") append("${launchClasspath.name} ")
                append(description)
            })
            .addStringOption("type", "Application")
            .addStringOption("factoryName", "Application")

        javaVersion?.let { jv ->
            val toolchain = project.extensions.getByType(JavaToolchainService::class.java)
            val launcher = toolchain.launcherFor { spec ->
                spec.languageVersion.set(JavaLanguageVersion.of(jv.majorVersion))
            }
            configuration.append(
                XMLBuilder("option").addStringOption("name", "ALTERNATIVE_JRE_PATH")
                    .addStringOption("value", launcher.get().metadata.installationPath.asFile.absolutePath),
                XMLBuilder("option").addStringOption("name", "ALTERNATIVE_JRE_PATH_ENABLED")
                    .addStringOption("value", "true")
            )
        }

        configuration.append(
            XMLBuilder("envs").append(
                *env.map { (key, value) ->
                    XMLBuilder("env").addStringOption("name", key).addStringOption("value", value)
                }.toTypedArray()
            ),
            XMLBuilder("option").addStringOption("name", "MAIN_CLASS_NAME")
                .addStringOption("value", mainClass),
            XMLBuilder("module").addStringOption(
                "name",
                "${
                    if (project != project.rootProject) "${project.rootProject.name}${
                        project.path.replace(
                            ":",
                            "."
                        )
                    }" else project.name
                }.${launchClasspath.name}"
            ),
            XMLBuilder("option").addStringOption("name", "PROGRAM_PARAMETERS")
                .addStringOption("value", args.joinToString(" ") { if (it.contains(" ")) "&quot;$it&quot;" else it }),
            XMLBuilder("option").addStringOption("name", "VM_PARAMETERS")
                .addStringOption(
                    "value",
                    jvmArgs.joinToString(" ") { if (it.contains(" ")) "&quot;$it&quot;" else it }),
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

        if (runFirst.isNotEmpty()) {
            mv2.append(
                XMLBuilder("option")
                    .addStringOption("name", "Gradle.BeforeRunTask")
                    .addStringOption("enabled", "true")
                    .addStringOption("tasks", runFirst.joinToString(" ") { it.name })
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
        }

        configuration.append(mv2)

        file.parentFile.mkdirs()
        file.writeText(
            XMLBuilder("component").addStringOption("name", "ProjectRunConfigurationManager").append(
                configuration
            ).toString(),
            StandardCharsets.UTF_8
        )
    }

    //TODO: add eclipse/vsc run configs

    fun createGradleTask(tasks: TaskContainer, group: String): Task {
        return tasks.create(taskName.withSourceSet(launchClasspath), JavaExec::class.java) {

            javaVersion?.let { jv ->
                val toolchain = project.extensions.getByType(JavaToolchainService::class.java)
                val launcher = toolchain.launcherFor { spec ->
                    spec.languageVersion.set(JavaLanguageVersion.of(jv.majorVersion))
                }
                it.javaLauncher.set(launcher)
            }

            it.environment.putAll(env)

            it.classpath = launchClasspath.runtimeClasspath

            it.args = args
            it.jvmArgs = jvmArgs

            it.group = group
            it.description = description
            it.mainClass.set(mainClass)
            it.workingDir = workingDir
            workingDir.mkdirs()

            if (runFirst.isNotEmpty()) {
                for (task in runFirst) {
                    it.dependsOn(task)
                }
            }
        }
    }
}