package xyz.wagyourtail.unimined.api.mappings

import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import kotlin.test.assertEquals

class MappingNamespaceTest {

    inline fun <reified T: Throwable> assertThrows(message: String? = null, runnable: () -> Unit) {
        try {
            runnable()
        } catch (e: Throwable) {
            if (e !is T) throw e
            if (message != null)
                assertEquals(message, e.message)
        }
    }


    @Test
    fun calculateShortestRemapPathInternalTests() {
        // remap from searge/official to mojmap/searge,
        // simulates 1.17+ forge remapping to the default ns used by forge (mojmap)
        assertEquals(
            listOf(
                false to MappingNamespace.MOJMAP
            ),
            MappingNamespace.calculateShortestRemapPathWithFallbacks(
                MappingNamespace.getNamespace("searge"),
                MappingNamespace.getNamespace("official"),
                MappingNamespace.getNamespace("searge"),
                MappingNamespace.getNamespace("mojmap"),
                setOf(
                    MappingNamespace.MOJMAP,
                    MappingNamespace.SEARGE,
                    MappingNamespace.OFFICIAL
                )
            )
        )

        // remap from searge/official to yarn/intermediary,
        // simulates 1.17+ forge remapping to fabric ns's (yarn/intermediary) for use in multi mapped projects
        // where yarn is prefered over mojmap
        assertEquals(
            listOf(
                true to MappingNamespace.OFFICIAL,
                false to MappingNamespace.INTERMEDIARY,
                false to MappingNamespace.YARN
            ),
            MappingNamespace.calculateShortestRemapPathWithFallbacks(
                MappingNamespace.getNamespace("searge"),
                MappingNamespace.getNamespace("official"),
                MappingNamespace.getNamespace("intermediary"),
                MappingNamespace.getNamespace("yarn"),
                setOf(
                    MappingNamespace.MOJMAP,
                    MappingNamespace.SEARGE,
                    MappingNamespace.OFFICIAL,
                    MappingNamespace.INTERMEDIARY,
                    MappingNamespace.YARN
                )
            )
        )

        // attempt remap from official directly to mcp.
        // this should fail as there is no direct path between the two.
        assertThrows<IllegalArgumentException>("Invalid target: MCP, available: [OFFICIAL, MCP]") {
            MappingNamespace.calculateShortestRemapPathWithFallbacks(
                MappingNamespace.getNamespace("official"),
                MappingNamespace.getNamespace("official"),
                MappingNamespace.getNamespace("mcp"),
                MappingNamespace.getNamespace("mcp"),
                setOf(
                    MappingNamespace.OFFICIAL,
                    MappingNamespace.MCP
                )
            )
        }

        // make MappingNamespace.OFFICIAL accept remaps to MappingNamespace.MCP
        val prev = MappingNamespace.OFFICIAL.canRemapTo
        MappingNamespace.OFFICIAL.canRemapTo = { ns -> prev(ns).let { if (!it.first && ns == MappingNamespace.MCP) true to false else it } }
        // remap from official to mcp
        assertEquals(
            listOf(
                false to MappingNamespace.MCP
            ),
            MappingNamespace.calculateShortestRemapPathWithFallbacks(
                MappingNamespace.getNamespace("official"),
                MappingNamespace.getNamespace("official"),
                MappingNamespace.getNamespace("mcp"),
                MappingNamespace.getNamespace("mcp"),
                setOf(
                    MappingNamespace.OFFICIAL,
                    MappingNamespace.SEARGE,
                    MappingNamespace.MCP
                )
            )
        )

    }

}