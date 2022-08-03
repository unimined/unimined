import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm") version "1.7.10"
    `java-gradle-plugin`
    `maven-publish`
}
base {
    archivesName.set(project.properties["archives_base_name"] as String)
}

version = project.properties["version"] as String
group = project.properties["maven_group"] as String

repositories {
    mavenCentral()
    maven {
        url = URI.create("https://maven.minecraftforge.net/")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    //guava
    implementation("com.google.guava:guava:31.1-jre")
    //gson
    implementation("com.google.code.gson:gson:2.9.0")

    // artifact transformer
    implementation("net.minecraftforge:artifactural:3.0.8")
}

tasks.jar {
    manifest {
        attributes.putAll(mapOf(
            "Implementation-Version" to project.version
        ))
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

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String

            from(components["java"])
        }
    }
}