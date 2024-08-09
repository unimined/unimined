import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.URI

plugins {
    kotlin("jvm") version "1.9.22"
    `java-gradle-plugin`
    `maven-publish`
}

version = if (project.hasProperty("version_snapshot")) project.properties["version"] as String + "-SNAPSHOT" else project.properties["version"] as String
group = project.properties["maven_group"] as String

base {
    archivesName.set(project.properties["archives_base_name"] as String)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

kotlin {
    jvmToolchain(8)
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.wagyourtail.xyz/snapshots/")
    maven {
        url = URI.create("https://maven.neoforged.net/releases")
    }
    maven {
        url = URI.create("https://maven.minecraftforge.net/")
    }
    maven {
        url = URI.create("https://maven.fabricmc.net/")
    }
    gradlePluginPortal()
}

fun SourceSet.inputOf(sourceSet: SourceSet) {
    compileClasspath += sourceSet.compileClasspath
    runtimeClasspath += sourceSet.runtimeClasspath
}

fun SourceSet.inputOf(vararg sourceSets: SourceSet) {
    for (sourceSet in sourceSets) {
        inputOf(sourceSet)
    }
}

fun SourceSet.outputOf(sourceSet: SourceSet) {
    compileClasspath += sourceSet.output
    runtimeClasspath += sourceSet.output
}

fun SourceSet.outputOf(vararg sourceSets: SourceSet) {
    for (sourceSet in sourceSets) {
        outputOf(sourceSet)
    }
}

sourceSets {
    create("api") {
        inputOf(main.get())
    }
    create("mapping") {
        inputOf(main.get())
        outputOf(
            sourceSets["api"]
        )
    }
    create("source") {
        inputOf(main.get())
        outputOf(
            sourceSets["mapping"],
            sourceSets["api"]
        )
    }
    create("mods") {
        inputOf(main.get())
        outputOf(
            sourceSets["api"],
            sourceSets["mapping"],
            sourceSets["source"]
        )
    }
    create("runs") {
        inputOf(main.get())
        outputOf(
            sourceSets["api"]
        )
    }
    create("minecraft") {
        inputOf(main.get())
        outputOf(
            sourceSets["api"],
            sourceSets["mapping"],
            sourceSets["mods"],
            sourceSets["runs"],
            sourceSets["source"]
        )
    }
    main {
        outputOf(
            sourceSets["api"],
            sourceSets["mapping"],
            sourceSets["source"],
            sourceSets["mods"],
            sourceSets["runs"],
            sourceSets["minecraft"],
        )
    }
    test {
        inputOf(
            main.get()
        )
        outputOf(
            sourceSets["api"],
            sourceSets["mapping"],
            sourceSets["source"],
            sourceSets["mods"],
            sourceSets["runs"],
            sourceSets["minecraft"],
            main.get()
        )
    }
}

dependencies {
    testImplementation(kotlin("test"))

    runtimeOnly(gradleApi())

    // kotlin metadata
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0") {
        isTransitive = false
    }

    // guava
    implementation("com.google.guava:guava:33.2.1-jre")

    // gson
    implementation("com.google.code.gson:gson:2.11.0")

    // asm
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-analysis:9.7")
    implementation("org.ow2.asm:asm-util:9.7")

    // remapper
    implementation("net.fabricmc:tiny-remapper:0.8.7") {
        exclude(group = "org.ow2.asm")
    }

    // mappings
    implementation("xyz.wagyourtail.unimined.mapping:unimined-mapping-library-jvm:1.0.0-SNAPSHOT")
    implementation("io.github.oshai:kotlin-logging:6.0.1")
    implementation("com.squareup.okio:okio:3.7.0")
    implementation("com.sschr15.annotations:jb-annotations-kmp:24.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // jetbrains annotations
    implementation("org.jetbrains:annotations-java5:24.1.0")

    // binpatcher
    implementation("net.minecraftforge:binarypatcher:1.1.1") {
        exclude(mapOf("group" to "commons-io"))
    }
    implementation("commons-io:commons-io:2.16.1")

    // pack200 provided by apache commons-compress
    implementation("org.apache.commons:commons-compress:1.26.1")

    // aw
    implementation("net.fabricmc:access-widener:2.1.0")

    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.2.202306221912-r")

    implementation("com.github.javakeyring:java-keyring:1.0.2")
    implementation("net.raphimc:MinecraftAuth:4.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    from(
        sourceSets["api"].output,
        sourceSets["mapping"].output,
        sourceSets["source"].output,
        sourceSets["mods"].output,
        sourceSets["runs"].output,
        sourceSets["minecraft"].output,
        sourceSets["main"].output
    )

    manifest {
        attributes(
            "Implementation-Version" to project.version
        )
    }
}

tasks.create("getArtifacts") {
    doLast {
        project.configurations.implementation.get().isCanBeResolved = true
        println(1)
        for (dependency in project.configurations.implementation.get().resolvedConfiguration.resolvedArtifacts) {
            println("${dependency.moduleVersion.id.group ?: "unknown"}:${dependency.name}:${dependency.moduleVersion.id.version ?: "unknown"}${dependency.classifier?.let { ":$it" } ?: ""}${dependency.extension?.let { "@$it" } ?: ""}")
        }
    }
}

tasks.create("sourcesJar", Jar::class) {
    archiveClassifier.set("sources")
    from(
        sourceSets["api"].allSource,
        sourceSets["minecraft"].allSource,
        sourceSets["mapping"].allSource,
        sourceSets["source"].allSource,
        sourceSets["mods"].allSource,
        sourceSets["runs"].allSource,
        sourceSets["main"].allSource
    )
    archiveClassifier.set("sources")
}

tasks.build {
    dependsOn("sourcesJar")
}

tasks.test {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    useJUnitPlatform()

    testLogging {
        events.add(TestLogEvent.PASSED)
        events.add(TestLogEvent.SKIPPED)
        events.add(TestLogEvent.FAILED)
    }
}

gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "xyz.wagyourtail.unimined"
            implementationClass = "xyz.wagyourtail.unimined.UniminedPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "WagYourMaven"
            url = if (project.hasProperty("version_snapshot")) {
                URI.create("https://maven.wagyourtail.xyz/snapshots/")
            } else {
                URI.create("https://maven.wagyourtail.xyz/releases/")
            }
            credentials {
                username = project.findProperty("mvn.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("mvn.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = project.properties["archives_base_name"] as String? ?: project.name
            version = project.version as String

            artifact(tasks["sourcesJar"]) {
                classifier = "sources"
            }
        }
    }
}

// A task to output a json file with a list of all the test to run
tasks.register("writeActionsTestMatrix") {
    doLast {
        val testMatrix = arrayListOf<String>()

        file("src/test/kotlin/xyz/wagyourtail/unimined/test/integration").listFiles()?.forEach {
            if (it.name.endsWith("Test.kt")) {

                val className = it.name.replace(".kt", "")
                testMatrix.add("xyz.wagyourtail.unimined.test.integration.${className}")
            }
        }

        testMatrix.add("xyz.wagyourtail.unimined.util.*")

        val json = groovy.json.JsonOutput.toJson(testMatrix)
        val output = file("build/test_matrix.json")
        output.parentFile.mkdir()
        output.writeText(json)
    }
}

/**
 * Replaces invalid characters in test names for GitHub Actions artifacts.
 */
abstract class PrintActionsTestName : DefaultTask() {
    @get:Input
    @get:Option(option = "name", description = "The test name")
    abstract val testName: Property<String>;

    @TaskAction
    fun run() {
        val sanitised = testName.get().replace('*', '_')
        File(System.getenv()["GITHUB_OUTPUT"]).writeText("\ntest=$sanitised")
    }
}

tasks.register<PrintActionsTestName>("printActionsTestName") {}
