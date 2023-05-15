

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal() {
            content {
                excludeGroup("org.apache.logging.log4j")
            }
        }
    }
}

rootProject.name = "unimined"