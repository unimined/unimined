import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm") version "1.8.21"
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
}

repositories {
    mavenCentral()
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
    create("mods") {
        inputOf(main.get())
        outputOf(
            sourceSets["api"],
            sourceSets["mapping"]
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
            sourceSets["runs"]
        )
    }
    main {
        outputOf(
            sourceSets["api"],
            sourceSets["mapping"],
            sourceSets["minecraft"]
        )
    }
    test {
        inputOf(
            main.get()
        )
        outputOf(
            sourceSets["api"],
            sourceSets["minecraft"],
            sourceSets["mapping"],
            sourceSets["mods"],
            sourceSets["runs"],
            main.get()
        )
    }
}

dependencies {
    testImplementation(kotlin("test"))

    runtimeOnly(gradleApi())

    // kotlin metadata
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.6.2") {
        isTransitive = false
    }

    // guava
    implementation("com.google.guava:guava:31.1-jre")

    // gson
    implementation("com.google.code.gson:gson:2.9.0")

    // asm
    implementation("org.ow2.asm:asm:9.5")
    implementation("org.ow2.asm:asm-commons:9.5")
    implementation("org.ow2.asm:asm-tree:9.5")
    implementation("org.ow2.asm:asm-analysis:9.5")
    implementation("org.ow2.asm:asm-util:9.5")

    // artifact transformer
    implementation("net.neoforged:artifactural:3.0.17") {
        exclude(group = "dev.gradleplugins", module = "gradle-api")
        exclude(group = "net.minecraftforge", module = "unsafe")
    }
    implementation("net.minecraftforge:unsafe:0.2.0") {
        exclude(group = "org.apache.logging.log4j")
    }
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")

    // remapper
    implementation("net.fabricmc:tiny-remapper:0.8.7") {
        exclude(group = "org.ow2.asm")
    }

    // mappings
    implementation("net.fabricmc:mapping-io:0.3.0") {
        exclude(group = "org.ow2.asm")
    }

    // jetbrains annotations
    implementation("org.jetbrains:annotations-java5:23.0.0")

    // binpatcher
    implementation("net.minecraftforge:binarypatcher:1.1.1") {
        exclude(mapOf("group" to "commons-io"))
    }
    implementation("commons-io:commons-io:2.12.0")

    // pack200 provided by apache commons-compress
    implementation("org.apache.commons:commons-compress:1.21")

    // aw
    implementation("net.fabricmc:access-widener:2.1.0")

    // at
    implementation("net.minecraftforge:accesstransformers:8.0.7") {
        exclude(group = "org.apache.logging.log4j")
        exclude(group = "org.ow2.asm")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.4.2") {
        isTransitive = false
    }
}

tasks.jar {
    from(
        sourceSets["api"].output,
        sourceSets["minecraft"].output,
        sourceSets["mapping"].output,
        sourceSets["mods"].output,
        sourceSets["runs"].output,
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
    useJUnitPlatform()

    testLogging {
        events.add(TestLogEvent.PASSED)
        events.add(TestLogEvent.SKIPPED)
        events.add(TestLogEvent.FAILED)
    }
}

tasks.withType<JavaCompile> {
    val targetVersion = 8
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(targetVersion)
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"

//    compilerOptions {
//        freeCompilerArgs.add("-Xjvm-default=all")
//    }
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
tasks.create("writeActionsTestMatrix") {
    doLast {
        val testMatrix = arrayListOf<String>()

        file("src/test/kotlin/xyz/wagyourtail/unimined/test/integration").listFiles()?.forEach {
            if (it.name.endsWith("Test.kt")) {

                val className = it.name.replace(".kt", "")
                testMatrix.add("xyz.wagyourtail.unimined.test.integration.${className}")
            }
        }

        testMatrix.add("xyz.wagyourtail.unimined.api.mappings.*")

        testMatrix.add("xyz.wagyourtail.unimined.util.*")

        val json = groovy.json.JsonOutput.toJson(testMatrix)
        val output = file("build/test_matrix.json")
        output.parentFile.mkdir()
        output.writeText(json)
    }
}

