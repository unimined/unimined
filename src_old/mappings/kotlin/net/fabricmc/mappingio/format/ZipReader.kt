package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

object ZipReader {

    fun getZipTypeFromContentList(zipContents: List<String>): MCPConfigVersion {
        val mappingFormats = mutableSetOf<MappingType>()
        for (value in MappingType.values()) {
            if (zipContents.any { it.matches(value.pattern) }) {
                mappingFormats.add(value)
            }
        }
        for (value in MCPConfigVersion.values()) {
            if (mappingFormats.containsAll(value.contains) && mappingFormats.none { value.doesntContain.contains(it) }) {
                return value
            }
        }
        throw IllegalArgumentException("No MCP config version detected, found: ${mappingFormats.joinToString { it.name }}")
    }

    private fun getTypeOf(path: String): MappingType? {
        for (value in MappingType.values()) {
            if (path.matches(value.pattern)) {
                return value
            }
        }
        return null
    }

    fun readMappings(
        envType: EnvType,
        zip: Path,
        zipContents: List<String>,
        mappingTree: MemoryMappingTree,
        named: MappingNamespace
    ) {
        val mcpConfigVersion = getZipTypeFromContentList(zipContents)
        println("Detected Zip Format: ${mcpConfigVersion.name} & envType: $envType")
        for (entry in zipContents.mapNotNull { getTypeOf(it)?.let { t -> Pair(t, it) } }
            .sortedBy { it.first.ordinal }
            .map { it.second }) {
            for (mappingType in MappingType.values()) {
                if (entry.matches(mappingType.pattern)) {
                    if (mcpConfigVersion.ignore.contains(mappingType)) {
                        break
                    }
                    println("Reading $entry")
                    when (mappingType) {
                        MappingType.MCP_METHODS -> {
                            when (mcpConfigVersion) {
                                MCPConfigVersion.OLD_MCP -> {
                                    readInputStreamFor(entry, zip) {
                                        OldMCPReader.readMethod(
                                            envType,
                                            InputStreamReader(it),
                                            MappingNamespace.OFFICIAL.namespace,
                                            MappingNamespace.SEARGE.namespace,
                                            MappingNamespace.MCP.namespace,
                                            mappingTree
                                        )
                                    }
                                }

                                MCPConfigVersion.OLDER_MCP -> {
                                    readInputStreamFor(entry, zip) {
                                        OlderMCPReader.readMethod(
                                            envType,
                                            InputStreamReader(it),
                                            MappingNamespace.SEARGE.namespace,
                                            MappingNamespace.MCP.namespace,
                                            mappingTree
                                        )
                                    }
                                }

                                else -> {
                                    readInputStreamFor(entry, zip) {
                                        MCPReader.readMethod(
                                            envType,
                                            InputStreamReader(it),
                                            MappingNamespace.SEARGE.namespace,
                                            MappingNamespace.MCP.namespace,
                                            mappingTree
                                        )
                                    }
                                }
                            }
                        }

                        MappingType.MCP_PARAMS -> {
                            readInputStreamFor(entry, zip) {
                                MCPReader.readParam(
                                    envType,
                                    InputStreamReader(it),
                                    MappingNamespace.SEARGE.namespace,
                                    MappingNamespace.MCP.namespace,
                                    mappingTree
                                )
                            }
                        }

                        MappingType.MCP_FIELDS -> {
                            when (mcpConfigVersion) {
                                MCPConfigVersion.OLD_MCP -> {
                                    readInputStreamFor(entry, zip) {
                                        OldMCPReader.readField(
                                            envType,
                                            InputStreamReader(it),
                                            MappingNamespace.OFFICIAL.namespace,
                                            MappingNamespace.SEARGE.namespace,
                                            MappingNamespace.MCP.namespace,
                                            mappingTree
                                        )
                                    }
                                }

                                MCPConfigVersion.OLDER_MCP -> {
                                    readInputStreamFor(entry, zip) {
                                        OlderMCPReader.readField(
                                            envType,
                                            InputStreamReader(it),
                                            MappingNamespace.SEARGE.namespace,
                                            MappingNamespace.MCP.namespace,
                                            mappingTree
                                        )
                                    }
                                }

                                else -> {
                                    readInputStreamFor(entry, zip) {
                                        MCPReader.readField(
                                            envType,
                                            InputStreamReader(it),
                                            MappingNamespace.SEARGE.namespace,
                                            MappingNamespace.MCP.namespace,
                                            mappingTree
                                        )
                                    }
                                }
                            }
                        }

                        MappingType.MCP_CLASSES -> {
                            readInputStreamFor(entry, zip) {
                                OldMCPReader.readClasses(
                                    envType,
                                    InputStreamReader(it),
                                    MappingNamespace.OFFICIAL.namespace,
                                    MappingNamespace.SEARGE.namespace,
                                    MappingNamespace.MCP.namespace,
                                    mappingTree
                                )
                            }
                        }

                        MappingType.SRG_CLIENT -> {
                            if (envType == EnvType.CLIENT) {
                                readInputStreamFor(entry, zip) {
                                    SrgReader.read(
                                        InputStreamReader(it),
                                        MappingNamespace.OFFICIAL.namespace,
                                        MappingNamespace.SEARGE.namespace,
                                        mappingTree
                                    )
                                }
                            } else {
                                println("Skipping SRG Client")
                            }
                        }

                        MappingType.SRG_SERVER -> {
                            if (envType == EnvType.SERVER) {
                                readInputStreamFor(entry, zip) {
                                    SrgReader.read(
                                        InputStreamReader(it),
                                        MappingNamespace.OFFICIAL.namespace,
                                        MappingNamespace.SEARGE.namespace,
                                        mappingTree
                                    )
                                }
                            } else {
                                println("Skipping SRG Server")
                            }
                        }

                        MappingType.SRG_MERGED -> {
//                            if (envType == EnvType.COMBINED) {
                            readInputStreamFor(entry, zip) {
                                SrgReader.read(
                                    InputStreamReader(it),
                                    MappingNamespace.OFFICIAL.namespace,
                                    MappingNamespace.SEARGE.namespace,
                                    mappingTree
                                )
                            }
//                            }
                        }

                        MappingType.RGS_CLIENT -> {
                            if (envType == EnvType.CLIENT) {
                                readInputStreamFor(entry, zip) {
                                    RGSReader.read(
                                        InputStreamReader(it),
                                        MappingNamespace.OFFICIAL.namespace,
                                        MappingNamespace.SEARGE.namespace,
                                        mappingTree
                                    )
                                }
                            } else {
                                println("Skipping RGS Client")
                            }
                        }

                        MappingType.RGS_SERVER -> {
                            if (envType == EnvType.SERVER) {
                                readInputStreamFor(entry, zip) {
                                    RGSReader.read(
                                        InputStreamReader(it),
                                        MappingNamespace.OFFICIAL.namespace,
                                        MappingNamespace.SEARGE.namespace,
                                        mappingTree
                                    )
                                }
                            } else {
                                println("Skipping RGS Server")
                            }
                        }

                        MappingType.TSRG -> {
                            readInputStreamFor(entry, zip) {
                                val temp = MemoryMappingTree()
                                TsrgReader.read(
                                    InputStreamReader(it),
                                    MappingNamespace.OFFICIAL.namespace,
                                    MappingNamespace.SEARGE.namespace,
                                    temp
                                )
                                temp.accept(
                                    MappingNsRenamer(
                                        mappingTree, mapOf(
                                            temp.srcNamespace to MappingNamespace.OFFICIAL.namespace,
                                        )
                                    )
                                )
                            }
                        }

                        MappingType.TINY -> {
                            readInputStreamFor(entry, zip) {
                                Tiny2Reader.read(
                                    InputStreamReader(it),
                                    MappingNsRenamer(
                                        mappingTree, mapOf(
                                            "official" to MappingNamespace.OFFICIAL.namespace,
                                            "intermediary" to MappingNamespace.INTERMEDIARY.namespace,
                                            "hashed" to MappingNamespace.HASHED.namespace,
                                            "named" to named.namespace
                                        )
                                    )
                                )
                            }
                        }

                        MappingType.MCP_PACKAGES -> {
                            readInputStreamFor(entry, zip) {
                                MCPReader.readPackages(
                                    envType,
                                    InputStreamReader(it),
                                    MappingNamespace.SEARGE.namespace,
                                    MappingNamespace.MCP.namespace,
                                    mappingTree
                                )
                            }
                        }

                        MappingType.PARCHMENT -> {
                            readInputStreamFor(entry, zip) {
                                ParchmentReader.read(
                                    InputStreamReader(it),
                                    MappingNamespace.MOJMAP.namespace,
                                    mappingTree
                                )
                            }
                        }
                    }
                    break
                }
            }
        }
    }

    fun readContents(zip: Path): List<String> {
        val contents = mutableListOf<String>()
        forEachInZip(zip) { entry, _ ->
            contents.add(entry)
        }
        return contents
    }

    fun forEachInZip(zip: Path, action: (String, InputStream) -> Unit) {
        ZipInputStream(zip.inputStream()).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                if (entry.isDirectory) {
                    entry = stream.nextEntry
                    continue
                }
                action(entry.name, stream)
                entry = stream.nextEntry
            }
        }
    }

    fun <T> readInputStreamFor(path: String, zip: Path, throwIfMissing: Boolean = true, action: (InputStream) -> T): T {
        ZipInputStream(zip.inputStream()).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                if (entry.isDirectory) {
                    entry = stream.nextEntry
                    continue
                }
                if (entry.name == path) {
                    return action(stream)
                }
                entry = stream.nextEntry
            }
        }
        if (throwIfMissing) {
            throw IllegalArgumentException("Missing file $path in $zip")
        }
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    fun openZipFileSystem(path: Path, args: Map<String, *> = mapOf<String, Any>()): FileSystem {
        if (!Files.exists(path) && args["create"] == true) {
            ZipOutputStream(path.outputStream()).use { stream ->
                stream.closeEntry()
            }
        }
        return FileSystems.newFileSystem(URI.create("jar:${path.toUri()}"), args, null)
    }

    enum class MappingType(val pattern: Regex) {
        TINY(Regex("""(.+[/\\]|^)mappings.tiny$""")),
        PARCHMENT(Regex("""(.+[/\\]|^)parchment.json$""")),
        SRG_CLIENT(Regex("""(.+[/\\]|^)client.srg$""")),
        SRG_SERVER(Regex("""(.+[/\\]|^)server.srg$""")),
        SRG_MERGED(Regex("""(.+[/\\]|^)joined.srg$""")),
        TSRG(Regex("""(.+[/\\]|^)joined.tsrg$""")),
        RGS_CLIENT(Regex("""(.+[/\\]|^)minecraft.rgs$""")), // see mcp28
        RGS_SERVER(Regex("""(.+[/\\]|^)minecraft_server.rgs$""")),
        MCP_CLASSES(Regex("""(.+[/\\]|^)classes.csv$""")), // see mcp43
        MCP_METHODS(Regex("""((.+[/\\]|^)|^)methods.csv$""")),
        MCP_PARAMS(Regex("""(.+[/\\]|^)params.csv$""")),
        MCP_FIELDS(Regex("""(.+[/\\]|^)fields.csv$""")),
        MCP_PACKAGES(Regex("""(.+[/\\]|^)packages.csv$""")),
        ;

        companion object {
            fun allBut(set: Set<MappingType>): Set<MappingType> {
                return values().toSet() - set
            }
        }
    }

    enum class MCPConfigVersion(
        val contains: Set<MappingType>,
        val doesntContain: Set<MappingType> = setOf(),
        val ignore: Set<MappingType> = setOf()
    ) {
        TINY_JAR(
            setOf(MappingType.TINY),
            MappingType.allBut(setOf(MappingType.TINY))
        ),
        PARCHMENT_ZIP(
            setOf(MappingType.PARCHMENT),
            MappingType.allBut(setOf(MappingType.PARCHMENT))
        ),
        NEW_MCPCONFIG(
            setOf(MappingType.TSRG),
            MappingType.allBut(setOf(MappingType.TSRG))
        ),
        MCPCONFIG(
            setOf(MappingType.SRG_MERGED),
            setOf(
                MappingType.MCP_FIELDS,
                MappingType.MCP_METHODS,
                MappingType.MCP_PARAMS,
                MappingType.MCP_CLASSES,
                MappingType.RGS_SERVER,
                MappingType.RGS_CLIENT
            )
        ),
        NEWFORGE_MCP(
            setOf(MappingType.MCP_METHODS, MappingType.MCP_PARAMS, MappingType.MCP_FIELDS),
            setOf(
                MappingType.MCP_CLASSES,
                MappingType.RGS_SERVER,
                MappingType.RGS_CLIENT,
                MappingType.SRG_SERVER,
                MappingType.SRG_CLIENT,
                MappingType.SRG_MERGED
            )
        ),
        MCP(
            setOf(MappingType.MCP_METHODS, MappingType.MCP_FIELDS),
            setOf(MappingType.RGS_CLIENT, MappingType.RGS_SERVER, MappingType.MCP_CLASSES, MappingType.TSRG),
        ),
        OLD_MCP(
            setOf(MappingType.MCP_METHODS, MappingType.MCP_FIELDS, MappingType.MCP_CLASSES),
            setOf(MappingType.RGS_CLIENT, MappingType.RGS_SERVER, MappingType.TSRG),
        ),
        OLDER_MCP(
            setOf(MappingType.RGS_CLIENT),
            setOf(MappingType.SRG_CLIENT, MappingType.SRG_SERVER, MappingType.SRG_MERGED, MappingType.TSRG),
            setOf(MappingType.MCP_CLASSES)
        ),
    }
}