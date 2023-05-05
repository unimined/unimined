package xyz.wagyourtail.unimined.api.mappings

import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import kotlin.test.assertEquals

class MappingNamespaceTest {

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

    }

}