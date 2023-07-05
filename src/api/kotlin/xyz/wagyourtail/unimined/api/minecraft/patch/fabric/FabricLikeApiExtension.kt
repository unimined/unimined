package xyz.wagyourtail.unimined.api.minecraft.patch.fabric

import org.w3c.dom.Document
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.stream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

open class FabricLikeApiExtension {

    abstract class APILocations {

        private val xmlDoc = defaultedMapOf<String, Document> { version ->
            val url = URI(getUrl(version))
            url.stream().use {
                val dbf = DocumentBuilderFactory.newInstance()
                val db = dbf.newDocumentBuilder()
                db.parse(it)
            }
        }

        abstract fun getUrl(version: String): String
        abstract fun getArtifactName(moduleName: String, version: String?): String

        fun module(moduleName: String, version: String): String? {
            val elements = xmlDoc[version].getElementsByTagName("dependency")
            for (i in 0 until elements.length) {
                val element = elements.item(i)
                var correct = false
                var vers: String? = null
                for (j in 0 until element.childNodes.length) {
                    val child = element.childNodes.item(j)
                    if (child.nodeName == "artifactId" && child.textContent == moduleName) {
                        correct = true
                    }
                    if (child.nodeName == "version") {
                        vers = child.textContent
                    }
                }
                if (correct) {
                    return getArtifactName(moduleName, vers)
                }
            }
            return null
        }

    }

    val locations = mapOf<String, APILocations>(
        "fabric" to object : APILocations() {
            override fun getUrl(version: String): String {
                return "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$version/fabric-api-$version.pom"
            }

            override fun getArtifactName(moduleName: String, version: String?): String {
                return "net.fabricmc.fabric-api:$moduleName:$version"
            }
        },
        "legacyFabric" to object : APILocations() {
            override fun getUrl(version: String): String {
                return "https://repo.legacyfabric.net/repository/legacyfabric/net/legacyfabric/legacy-fabric-api/legacy-fabric-api/$version/legacy-fabric-api-$version.pom"
            }

            override fun getArtifactName(moduleName: String, version: String?): String {
                return "net.legacyfabric.legacy-fabric-api:$moduleName:$version"
            }
        },
        "quilt" to object : APILocations() {
            override fun getUrl(version: String): String {
                return "https://maven.quiltmc.org/repository/release/org/quiltmc/quilted-fabric-api/quilted-fabric-api/$version/quilted-fabric-api-$version.pom"
            }

            override fun getArtifactName(moduleName: String, version: String?): String {
                return "org.quiltmc.quilted-fabric-api:$moduleName:$version"
            } 
        },
        "qsl" to object : APILocations() {
            override fun getUrl(version: String): String {
                return "https://maven.quiltmc.org/repository/release/org/quiltmc/qsl/$version/qsl-$version.pom"
            }

            override fun getArtifactName(moduleName: String, version: String?): String {
                return "org.quiltmc.qsl:$moduleName:$version"
            } 
        }
    )

    @Deprecated(message = "use fabricModule or legacyFabricModule instead", replaceWith = ReplaceWith("fabricModule"))
    fun module(moduleName: String, version: String): String {
        return locations.values.firstNotNullOfOrNull { it.module(moduleName, version) } ?: throw IllegalStateException("Could not find module $moduleName:$version")
    }


    /**
     * @since 1.0.0
     */
    fun fabricModule(moduleName: String, version: String): String {
        return locations["fabric"]!!.module(moduleName, version) ?: throw IllegalStateException("Could not find module $moduleName:$version")
    }


    /**
     * @since 1.0.0
     */
    fun quiltFabricModule(moduleName: String, version: String): String {
        return locations["quilt"]!!.module(moduleName, version) ?: throw IllegalStateException("Could not find module $moduleName:$version")
    }


    /**
     * @since 1.0.0
     */
    fun qslModule(moduleName: String, version: String): String {
        return locations["qsl"]!!.module(moduleName, version) ?: throw IllegalStateException("Could not find module $moduleName:$version")
    }


    /**
     * @since 1.0.0
     */
    fun legacyFabricModule(moduleName: String, version: String): String {
        return locations["legacyFabric"]!!.module(moduleName, version) ?: throw IllegalStateException("Could not find module $moduleName:$version")
    }

}
