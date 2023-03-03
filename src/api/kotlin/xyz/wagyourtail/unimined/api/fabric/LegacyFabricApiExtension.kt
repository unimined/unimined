package xyz.wagyourtail.unimined.api.fabric

import org.gradle.api.Project

/**
 * helper class for getting legacy fabric api parts.
 *
 * @since 0.4.2
 *
 * usage:
 * ```groovy
 * dependencies {
 *    ...
 *    // legacy fabric api part
 *    modImplementation legacyFabricApi.module("fabric-api-base", "0.67.1+1.19.2")
 * }
 */
@Suppress("unused")
open class LegacyFabricApiExtension : FabricLikeApiExtension("legacy-fabric-api") {
    companion object {
        fun apply(target: Project) {
            target.extensions.create("legacyFabricApi", LegacyFabricApiExtension::class.java)
        }
    }

    override fun getUrl(version: String): String {
        return "https://repo.legacyfabric.net/repository/legacyfabric/net/legacyfabric/legacy-fabric-api/legacy-fabric-api/$version/legacy-fabric-api-$version.pom"
    }

    override fun getArtifactName(moduleName: String, version: String?): String {
        return "net.legacyfabric.legacy-fabric-api:$moduleName:$version"
    }
}