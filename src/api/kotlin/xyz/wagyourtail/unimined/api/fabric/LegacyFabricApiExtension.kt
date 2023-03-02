package xyz.wagyourtail.unimined.api.fabric

import org.gradle.api.Project
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

/**
 * helper class for getting legacy fabric api parts.
 *
 * @since 0.4.2
 *
 * usage:
 * ```groovy
 * dependencies {
 *    ...
 *    // fabric api part
 *    modImplementation legacyFabricApi.module("fabric-api-base", "0.67.1+1.19.2")
 * }
 */
@Suppress("unused")
open class LegacyFabricApiExtension {
    companion object {
        fun apply(target: Project) {
            target.extensions.create("legacyFabricApi", LegacyFabricApiExtension::class.java)
        }
    }

    fun module(name: String, version: String): String {
        val url = URL("https://repo.legacyfabric.net/repository/legacyfabric/net/legacyfabric/legacy-fabric-api/legacy-fabric-api/$version/legacy-fabric-api-$version.pom")
        url.openStream().use {
            val dbf = DocumentBuilderFactory.newInstance()
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(it)
            val elements = doc.getElementsByTagName("dependency")
            for (i in 0 until elements.length) {
                val element = elements.item(i)
                var correct = false
                var vers: String? = null
                for (j in 0 until element.childNodes.length) {
                    val child = element.childNodes.item(j)
                    if (child.nodeName == "artifactId" && child.textContent == name) {
                        correct = true
                    }
                    if (child.nodeName == "version") {
                        vers = child.textContent
                    }
                }
                if (correct) {
                    return "net.legacyfabric.legacy-fabric-api:$name:$vers"
                }
            }
        }
        throw IllegalStateException("Could not find module $name in legacy-fabric-api $version")
    }
}