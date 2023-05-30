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
            sourceSets["mapping"],
            sourceSets["minecraft"],
            main.get()
        )
    }
}

dependencies {
    testImplementation(kotlin("test"))

    runtimeOnly(gradleApi())

    // guava
    implementation("com.google.guava:guava:31.1-jre")

    // gson
    implementation("com.google.code.gson:gson:2.9.0") {

    }

    // artifact transformer
    implementation("net.minecraftforge:artifactural:3.0.10")
    implementation("net.minecraftforge:unsafe:0.2.0")

    // remapper
    implementation("net.fabricmc:tiny-remapper:0.8.6")

    // mappings
    implementation("net.fabricmc:mapping-io:0.3.0")

    // jetbrains annotations
    implementation("org.jetbrains:annotations-java5:23.0.0")

    // binpatcher
    implementation("net.minecraftforge:binarypatcher:1.1.1")

    // pack200 provided by apache commons-compress
    implementation("org.apache.commons:commons-compress:1.21")

    // aw
    implementation("net.fabricmc:access-widener:2.1.0")

    // at
    implementation("net.minecraftforge:accesstransformers:8.0.7")

    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.4.2") {
        isTransitive = false
    }

    // class transform
    implementation("net.lenni0451.classtransform:core:1.8.4")
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

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "xyz.wagyourtail.unimined"
            implementationClass = "xyz.wagyourtail.unimined.UniminedPlugin"
        }
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

            from(components["java"])
        }
    }
}