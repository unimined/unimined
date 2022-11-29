package xyz.wagyourtail.unimined.api.fabric

import org.gradle.api.Project
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

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
open class FabricApiExtension {
    companion object {
        fun apply(target: Project) {
            target.extensions.create("fabricApi", FabricApiExtension::class.java)
        }
    }

    fun module(name: String, version: String): String {
        val url = URL("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$version/fabric-api-$version.pom")
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
                    return "net.fabricmc.fabric-api:$name:$vers"
                }
            }
        }
        throw IllegalStateException("Could not find module $name in fabric-api $version")
    }
}