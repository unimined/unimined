package xyz.wagyourtail.unimined.api.mappings

import org.junit.jupiter.api.Test
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
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
    fun remapToForgeOfficial() {
        // remap from searge/official to mojmap/searge,
        // simulates 1.17+ forge remapping to the default ns used by forge (mojmap)
        val mnt = MappingNamespaceTree()
        val SEARGE = mnt.addNamespace("searge", { setOf(mnt.OFFICIAL) }, false)
        val MOJMAP = mnt.addNamespace("mojmap", { setOf(mnt.OFFICIAL, SEARGE) }, true)
        assertEquals(
            listOf(
                MOJMAP
            ),
            mnt.getRemapPath(
                SEARGE,
                mnt.OFFICIAL,
                SEARGE,
                MOJMAP
            )
        )
    }

    @Test
    fun remapFromForgeToYarn() {
        // remap from searge/official to yarn/intermediary,
        // simulates 1.17+ forge remapping to fabric ns's (yarn/intermediary) for use in multi mapped projects
        // where yarn is prefered over mojmap
        val mnt = MappingNamespaceTree()
        val SEARGE = mnt.addNamespace("searge", { setOf(mnt.OFFICIAL) }, false)
        val INTERMEDIARY = mnt.addNamespace("intermediary", { setOf(mnt.OFFICIAL) }, false)
        val YARN = mnt.addNamespace("yarn", { setOf(INTERMEDIARY) }, true)
        assertEquals(
            listOf(
                mnt.OFFICIAL,
                INTERMEDIARY,
                YARN
            ),
            mnt.getRemapPath(
                SEARGE,
                mnt.OFFICIAL,
                INTERMEDIARY,
                YARN
            )
        )
    }


    @Test
    fun failRemapFromOfficialToMCP() {
        val mnt = MappingNamespaceTree()
        val MCP = mnt.addNamespace("mcp", { emptySet() }, true)
        // attempt remap from official directly to mcp.
        // this should fail as there is no direct path between the two.
        assertThrows<IllegalArgumentException>("""
        Cannot remap from "official" to "mcp". MappingNamespaceTree.MappingNamespace: {
          official -> []
          mcp -> []
        }
        """.trimIndent()) {
            mnt.getRemapPath(
                mnt.OFFICIAL,
                mnt.OFFICIAL,
                mnt.OFFICIAL,
                MCP
            )
        }
    }

    @Test
    fun remapFromOfficialDirectToMCP() {
        val mnt = MappingNamespaceTree()
        // make MappingNamespace.OFFICIAL accept remaps to MappingNamespace.MCP
        val MCP = mnt.addNamespace("mcp", { setOf(mnt.OFFICIAL) }, true)
        // remap from official to mcp
        assertEquals(
            listOf(
                MCP
            ),
            mnt.getRemapPath(
                mnt.OFFICIAL,
                mnt.OFFICIAL,
                mnt.OFFICIAL,
                MCP
            )
        )
    }

    @Test
    fun remapFromMCPtoMojmap() {
        val mnt = MappingNamespaceTree()
        val SEARGE = mnt.addNamespace("searge", { setOf(mnt.OFFICIAL) }, false)
        val MCP = mnt.addNamespace("mcp", { setOf(mnt.OFFICIAL, SEARGE) }, true)
        val MOJMAP = mnt.addNamespace("mojmap", { setOf(mnt.OFFICIAL, SEARGE) }, true)
        assertEquals(
            listOf(
                SEARGE,
                MOJMAP
            ),
            mnt.getRemapPath(
                MCP,
                SEARGE,
                SEARGE,
                MOJMAP
            )
        )
    }

    @Test
    fun remapFromSeargeToMCP() {
        val mnt = MappingNamespaceTree()
        val SEARGE = mnt.addNamespace("searge", { setOf(mnt.OFFICIAL) }, false)
        val MCP = mnt.addNamespace("mcp", { setOf(mnt.OFFICIAL, SEARGE) }, true)
        assertEquals(
            listOf(
                MCP
            ),
            mnt.getRemapPath(
                SEARGE,
                mnt.OFFICIAL,
                SEARGE,
                MCP
            )
        )
    }

}