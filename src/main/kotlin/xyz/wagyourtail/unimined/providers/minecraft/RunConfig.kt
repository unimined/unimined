package xyz.wagyourtail.unimined.providers.minecraft

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.XMLBuilder
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.path.relativeTo

data class RunConfig(
    val project: Project,
    val taskName: String,
    var description: String,
    val classpath: SourceSet,
    var mainClass: String,
    val args: MutableList<String>,
    val jvmArgs: MutableList<String>,
    val workingDir: File,
    val env: MutableMap<String, String>,
) {

    fun createIdeaRunConfig(javaVersion: JavaVersion) {
        val file = project.projectDir.resolve(".idea").resolve("runConfigurations").resolve("$taskName.xml")
        file.parentFile.mkdirs()
        file.writeText(
            XMLBuilder("component").addStringOption("name", "ProjectRunConfigurationManager").append(
                XMLBuilder("configuration").addStringOption("default", "false").addStringOption("name", description).addStringOption("type", "Application").addStringOption("factoryName", "Application").append(
                    XMLBuilder("option").addStringOption("name", "ALTERNATIVE_JRE_PATH").addStringOption("value", javaVersion.toString()),
                    XMLBuilder("option").addStringOption("name", "ALTERNATIVE_JRE_PATH_ENABLED").addStringOption("value", "true"),
                    XMLBuilder("envs").append(
                        *env.map { (key, value) ->
                            XMLBuilder("env").addStringOption("name", key).addStringOption("value", value)
                        }.toTypedArray()
                    ),
                    XMLBuilder("option").addStringOption("name", "MAIN_CLASS_NAME").addStringOption("value", mainClass),
                    XMLBuilder("module").addStringOption("name", "${project.name}.${classpath.name}"),
                    XMLBuilder("option").addStringOption("name", "WORKING_DIRECTORY").addStringOption("value", "\$PROJECT_DIR\$/${workingDir.toPath().relativeTo(project.rootProject.projectDir.toPath())}"),
                    XMLBuilder("option").addStringOption("name", "VM_PARAMETERS").addStringOption("value", jvmArgs.joinToString(" ") { if (it.contains(" ")) "&quot;$it&quot;" else it }),
                    XMLBuilder("option").addStringOption("name", "PROGRAM_PARAMETERS").addStringOption("value", args.joinToString(" ")),
                    XMLBuilder("method").addStringOption("v", "2").append(
                        XMLBuilder("option").addStringOption("name", "Make").addStringOption("enabled", "true")
                    )
                )
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

            it.classpath = classpath.runtimeClasspath

            it.args = args
            it.jvmArgs = jvmArgs
        }
    }
}