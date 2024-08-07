package xyz.wagyourtail.unimined.test.integration

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import xyz.wagyourtail.unimined.api.minecraft.patch.fabric.FabricLikeApiExtension
import xyz.wagyourtail.unimined.util.createFakeProject
import kotlin.test.assertEquals

class FabricLikeApiExtensionTest {
    companion object {
        lateinit var extension: FabricLikeApiExtension

        @JvmStatic
        @BeforeAll
        fun init() {
            extension = FabricLikeApiExtension(createFakeProject())
        }
    }

    @ParameterizedTest(name = "fabric API full {0}")
    @CsvSource(
        "0.100.8+1.21",
        "0.42.0+1.16",
        "0.28.5+1.14"
    )
    fun fabricFull(version: String) {
        val result = extension.fabric(version)
        assertEquals("net.fabricmc.fabric-api:fabric-api:$version", result)
    }

    @ParameterizedTest(name = "fabric API {0} module {1}")
    @CsvSource(
        "0.100.8+1.21,  fabric-command-api-v2,      2.2.28+6ced4dd9d1",
        "0.42.0+1.16,   fabric-entity-events-v1,    1.2.4+3cc0f0907d",
        "0.28.5+1.14,   fabric-events-lifecycle-v0, 0.2.1+4ea4772942"
    )
    fun fabricModule(apiVersion: String, module: String, moduleVersion: String) {
        val result = extension.fabricModule(module, apiVersion)
        assertEquals("net.fabricmc.fabric-api:$module:$moduleVersion", result)
    }

    @ParameterizedTest(name = "quilted fabric API full {0}")
    @CsvSource(
        "11.0.0-alpha.3+0.100.7-1.21",
        "6.0.0-beta.11+0.87.0-1.19.4",
        "1.0.0-beta.28+0.67.0-1.18.2"
    )
    fun quiltFabricFull(version: String) {
        val result = extension.quiltFabric(version)
        assertEquals("org.quiltmc.quilted-fabric-api:quilted-fabric-api:$version", result)
    }

    @ParameterizedTest(name = "quilted fabric API {0} module {2}")
    @CsvSource(
        "11.0.0-alpha.3+0.100.7-1.21, quilted-fabric-api,   fabric-data-attachment-api-v1,  11.0.0-alpha.3+0.100.7-1.21",
        "6.0.0-beta.11+0.87.0-1.19.4, quilted-fabric-api,   fabric-item-api-v1,             6.0.0-beta.11+0.87.0-1.19.4",
        "6.0.0-beta.11+0.87.0-1.19.4, qsl.block,            block_extensions,               5.0.0-beta.11+1.19.4",
        "1.0.0-beta.28+0.67.0-1.18.2, quilted-fabric-api,   fabric-convention-tags-v1,      1.0.0-beta.28+0.67.0-1.18.2",
        "1.0.0-beta.28+0.67.0-1.18.2, qsl.core,             resource_loader,                1.1.0-beta.26+1.18.2"
    )
    fun quiltFabricModule(apiVersion: String, moduleParent: String, module: String, moduleVersion: String) {
        val result = extension.quiltFabricModule(module, apiVersion)
        assertEquals("org.quiltmc.$moduleParent:$module:$moduleVersion", result)
    }

    @ParameterizedTest(name = "qsl full {0}")
    @CsvSource(
        "10.0.0-alpha.1+1.21",
        "5.0.0-beta.11+1.19.4",
        "1.1.0-beta.26+1.18.2"
    )
    fun qslFull(version: String) {
        val result = extension.qsl(version)
        assertEquals("org.quiltmc:qsl:$version", result)
    }

    @ParameterizedTest(name = "qsl {0} module {1}")
    @CsvSource(
        "10.0.0-alpha.1+1.21,   block,      10.0.0-alpha.1+1.21",
        "5.0.0-beta.11+1.19.4,  item,       5.0.0-beta.11+1.19.4",
        "5.0.0-beta.11+1.19.4,  rendering,  5.0.0-beta.11+1.19.4",
        "1.1.0-beta.26+1.18.2,  data,       1.1.0-beta.26+1.18.2",
        "1.1.0-beta.26+1.18.2,  misc,       1.1.0-beta.26+1.18.2"
    )
    fun qslModule(apiVersion: String, module: String, moduleVersion: String) {
        val result = extension.qslModule(module, apiVersion)
        assertEquals("org.quiltmc.qsl:$module:$moduleVersion", result)
    }

    @ParameterizedTest(name = "LFAPI full {0}")
    @CsvSource(
        "1.10.2+1.6.4",
        "1.7.1+1.12.2",
        "1.0.0+1.8.9"
    )
    fun legacyFabricFull(version: String) {
        val result = extension.legacyFabric(version)
        assertEquals("net.legacyfabric.legacy-fabric-api:legacy-fabric-api:$version", result)
    }

    @ParameterizedTest(name = "LFAPI {0} module {1}")
    @CsvSource(
        "1.10.2+1.6.4,  legacy-fabric-keybindings-api-v1,   1.1.1+1.6.4+281301eab14f",
        "1.7.1+1.12.2,  legacy-fabric-item-groups-v1,       2.0.0+1.12.2+ae4aa0d052",
        "1.0.0+1.8.9,   legacy-fabric-lifecycle-events-v1,  1.0.0+uncommited"
    )
    fun legacyFabricModule(apiVersion: String, module: String, moduleVersion: String) {
        val result = extension.legacyFabricModule(module, apiVersion)
        assertEquals("net.legacyfabric.legacy-fabric-api:$module:$moduleVersion", result)
    }

    @ParameterizedTest(name = "StationAPI full {0}")
    @CsvSource(
        "2.0-alpha.2.1, releases",
        "2.0-alpha.1,   releases",
        "6143ce4,       snapshots",
        "2988528,       snapshots"
    )
    fun stationFull(version: String, branch: String) {
        val result = extension.station(branch, version)
        assertEquals("net.modificationstation:StationAPI:$version", result)
    }

    @ParameterizedTest(name = "StationAPI {0} module {3}")
    @CsvSource(
        "2.0-alpha.2.1,     releases,   submodule.station-maths-v0, station-maths-v0,               2.0-alpha.2.1-1.0.0",
        "2.0-alpha.1,       releases,   2.0-alpha.1,                station-blockitems-v0,          2.0-alpha.1-1.0.0",
        "6143ce4,           snapshots,  6143ce4,                    station-lifecycle-events-v0,    2.0-PRE2-1.0.0",
        "2988528,           snapshots,  2988528,                    station-dimensions-v0,          2.0-PRE2-1.0.0"
    )
    fun stationModule(apiVersion: String, branch: String, moduleParent: String, module: String, moduleVersion: String) {
        val result = extension.stationModule(branch, module, apiVersion)
        assertEquals("net.modificationstation.StationAPI.$moduleParent:$module:$moduleVersion", result)
    }
}