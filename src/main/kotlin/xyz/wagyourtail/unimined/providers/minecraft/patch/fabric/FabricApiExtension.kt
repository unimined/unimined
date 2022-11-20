package xyz.wagyourtail.unimined.providers.minecraft.patch.fabric

import org.gradle.api.Project
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

open class FabricApiExtension(project: Project) {
    companion object {
        fun apply(target: Project) {
            target.extensions.create("fabricApi", FabricApiExtension::class.java, target)
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
                for (j in 0 until element.childNodes.length) {
                    val child = element.childNodes.item(j)
                    if (child.nodeName == "artifactId" && child.textContent == name) {
                        return element.childNodes.item(1).textContent
                    }
                }
            }
        }
        throw IllegalStateException("Could not find module $name in fabric-api $version")
    }
}