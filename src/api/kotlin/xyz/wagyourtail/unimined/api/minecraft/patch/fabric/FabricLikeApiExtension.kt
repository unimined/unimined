package xyz.wagyourtail.unimined.api.minecraft.patch.fabric

import org.gradle.api.Project
import org.w3c.dom.Document
import xyz.wagyourtail.unimined.util.cachingDownload
import xyz.wagyourtail.unimined.util.defaultedMapOf
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.inputStream

open class FabricLikeApiExtension(val project: Project) {

    // TODO: cache so that offline mode works

    abstract class APILocations(val project: Project) {

        internal val xmlDoc = defaultedMapOf<String, Document> { version ->
            project.cachingDownload(
                URI.create(getUrl(version))
            ).inputStream().use {
                val dbf = DocumentBuilderFactory.newInstance()
                val db = dbf.newDocumentBuilder()
                db.parse(it)
            }
        }

        abstract fun getUrl(version: String): String
        abstract fun full(version: String): String

        open fun module(moduleName: String, version: String): String? {
            val elements = xmlDoc[version].getElementsByTagName("dependency")
            for (i in 0 until elements.length) {
                val element = elements.item(i)
                var correct = false
                var group: String? = null
                var vers: String? = null
                for (j in 0 until element.childNodes.length) {
                    val child = element.childNodes.item(j)
                    if (child.nodeName == "artifactId" && child.textContent == moduleName) {
                        correct = true
                    }
                    if (child.nodeName == "groupId") {
                        group = child.textContent
                    }
                    if (child.nodeName == "version") {
                        vers = child.textContent
                    }
                }
                if (correct) {
                    return "$group:$moduleName:$vers"
                }
            }
            return null
        }

    }

    open class StAPILocation(project: Project, private val branch: String) : APILocations(project) {
        override fun getUrl(version: String): String {
            return "https://maven.glass-launcher.net/$branch/net/modificationstation/StationAPI/$version/StationAPI-$version.pom"
        }

        override fun full(version: String): String {
            return "net.modificationstation:StationAPI:$version"
        }
    }

    val locations = mapOf<String, APILocations>(
        "fabric" to object : APILocations(project) {
            override fun getUrl(version: String): String {
                return "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$version/fabric-api-$version.pom"
            }

            override fun full(version: String): String {
                return "net.fabricmc.fabric-api:fabric-api:$version"
            }
        },
        "legacyFabric" to object : APILocations(project) {
            override fun getUrl(version: String): String {
                return "https://repo.legacyfabric.net/repository/legacyfabric/net/legacyfabric/legacy-fabric-api/legacy-fabric-api/$version/legacy-fabric-api-$version.pom"
            }

            override fun full(version: String): String {
                return "net.legacyfabric.legacy-fabric-api:legacy-fabric-api:$version"
            }
        },
        "quilt" to object : APILocations(project) {
            override fun getUrl(version: String): String {
                return "https://maven.quiltmc.org/repository/release/org/quiltmc/quilted-fabric-api/quilted-fabric-api/$version/quilted-fabric-api-$version.pom"
            }

            override fun full(version: String): String {
                return "org.quiltmc.quilted-fabric-api:quilted-fabric-api:$version"
            }
        },
        "qsl" to object : APILocations(project) {
            override fun getUrl(version: String): String {
                return "https://maven.quiltmc.org/repository/release/org/quiltmc/qsl/$version/qsl-$version.pom"
            }

            override fun full(version: String): String {
                return "org.quiltmc:qsl:$version"
            }
        },
        "station_snapshots" to object : StAPILocation(project, "snapshots") {},
        "station_releases" to object : StAPILocation(project, "releases") {}
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
     * @since 1.3.0
     */
    fun fabric(version: String): String {
        return locations["fabric"]!!.full(version)
    }


    /**
     * @since 1.0.0
     */
    fun quiltFabricModule(moduleName: String, version: String): String {
        return locations["quilt"]!!.module(moduleName, version) ?: throw IllegalStateException("Could not find module $moduleName:$version")
    }

    /**
     * @since 1.3.0
     */
    fun quiltFabric(version: String): String {
        return locations["quilt"]!!.full(version)
    }


    /**
     * @since 1.0.0
     */
    fun qslModule(moduleName: String, version: String): String {
        return locations["qsl"]!!.module(moduleName, version) ?: throw IllegalStateException("Could not find module $moduleName:$version")
    }

    /**
     * @since 1.3.0
     */
    fun qsl(version: String): String {
        return locations["qsl"]!!.full(version)
    }


    /**
     * @since 1.0.0
     */
    fun legacyFabricModule(moduleName: String, version: String): String {
        return locations["legacyFabric"]!!.module(moduleName, version) ?: throw IllegalStateException("Could not find module $moduleName:$version")
    }

    /**
     * @since 1.3.0
     */
    fun legacyFabric(version: String): String {
        return locations["legacyFabric"]!!.full(version)
    }

    /**
     * @since 1.0.0
     */
    @JvmOverloads
    fun stationModule(branch: String = "snapshots", moduleName: String, version: String): String {
        return locations["station_$branch"]!!.module(moduleName, version) ?: throw IllegalStateException("Could not find module $moduleName:$version")
    }

    @JvmOverloads
    fun station(branch: String = "snapshots", version: String): String {
        return locations["station_$branch"]!!.full(version)
    }

}
