package xyz.wagyourtail.unimined.api.fabric

import org.gradle.api.Project

/**
 * helper class for getting fabric api parts.
 *
 * @since 0.2.1
 *
 * usage:
 * ```groovy
 * dependencies {
 *    ...
 *    // fabric api part
 *    modImplementation fabricApi.module("fabric-api-base", "0.67.1+1.19.2")
 * }
 */
@Suppress("unused")
open class FabricApiExtension: FabricLikeApiExtension("fabric-api") {
    companion object {
        fun apply(target: Project) {
            target.extensions.create("fabricApi", FabricApiExtension::class.java)
        }
    }

    override fun getUrl(version: String): String {
        return "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$version/fabric-api-$version.pom"
    }

    override fun getArtifactName(moduleName: String, version: String?): String {
        return "net.fabricmc.fabric-api:$moduleName:$version"
    }
}