package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.inputStream

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
        throw IllegalArgumentException("No MCP config version detected")
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
        envType: EnvType, zip: Path, zipContents: List<String>, mappingTree: MemoryMappingTree,
        notchNamespaceName: String = "official", seargeNamespaceName: String = "searge", mCPNamespaceName: String = "named"
    ) {
        val mcpConfigVersion = getZipTypeFromContentList(zipContents)
        System.out.println("Detected Zip Format: ${mcpConfigVersion.name} & envType: $envType")
        createZipFS(zip).use { fs ->
            for (entry in zipContents.mapNotNull { getTypeOf(it)?.let { t-> Pair(t, it) } }.sortedBy { it.first.ordinal }.map { it.second }) {
                for (mappingType in MappingType.values()) {
                    if (entry.matches(mappingType.pattern)) {
                        if (mcpConfigVersion.ignore.contains(mappingType)) {
                            break
                        }
                        System.out.println("Reading $entry")
                        when (mappingType) {
                            MappingType.MCP_METHODS -> {
                                when (mcpConfigVersion) {
                                    MCPConfigVersion.OLD_MCP -> {
                                        OldMCPReader.readMethod(
                                            envType,
                                            InputStreamReader(fs.getPath(entry).inputStream()),
                                            notchNamespaceName,
                                            seargeNamespaceName,
                                            mCPNamespaceName,
                                            mappingTree
                                        )
                                    }

                                    MCPConfigVersion.OLDER_MCP -> {
//                                OlderMCPReader.readMethod(
//                                    InputStreamReader(inputStream),
//                                    seargeNamespaceName,
//                                    mCPNamespaceName,
//                                    mappingTree
//                                )
                                    }

                                    else -> {
                                        MCPReader.readMethod(
                                            envType,
                                            InputStreamReader(fs.getPath(entry).inputStream()),
                                            seargeNamespaceName,
                                            mCPNamespaceName,
                                            mappingTree
                                        )
                                    }
                                }
                            }

                            MappingType.MCP_PARAMS -> {
                                MCPReader.readParam(
                                    envType,
                                    InputStreamReader(fs.getPath(entry).inputStream()),
                                    seargeNamespaceName,
                                    mCPNamespaceName,
                                    mappingTree
                                )
                            }

                            MappingType.MCP_FIELDS -> {
                                when (mcpConfigVersion) {
                                    MCPConfigVersion.OLD_MCP -> {
                                        OldMCPReader.readField(
                                            envType,
                                            InputStreamReader(fs.getPath(entry).inputStream()),
                                            notchNamespaceName,
                                            seargeNamespaceName,
                                            mCPNamespaceName,
                                            mappingTree
                                        )
                                    }

                                    MCPConfigVersion.OLDER_MCP -> {
//                                OlderMCPReader.readField(
//                                    InputStreamReader(inputStream),
//                                    seargeNamespaceName,
//                                    mCPNamespaceName,
//                                    mappingTree
//                                )
                                    }

                                    else -> {
                                        MCPReader.readField(
                                            envType,
                                            InputStreamReader(fs.getPath(entry).inputStream()),
                                            seargeNamespaceName,
                                            mCPNamespaceName,
                                            mappingTree
                                        )
                                    }
                                }
                            }

                            MappingType.MCP_CLASSES -> {
                                OldMCPReader.readClasses(
                                    envType,
                                    InputStreamReader(fs.getPath(entry).inputStream()),
                                    notchNamespaceName,
                                    seargeNamespaceName,
                                    mCPNamespaceName,
                                    mappingTree
                                )
                            }

                            MappingType.SRG_CLIENT -> {
                                if (envType == EnvType.CLIENT) {
                                    SrgReader.read(
                                        InputStreamReader(fs.getPath(entry).inputStream()),
                                        notchNamespaceName,
                                        seargeNamespaceName,
                                        mappingTree
                                    )
                                }
                            }

                            MappingType.SRG_SERVER -> {
                                if (envType == EnvType.SERVER) {
                                    SrgReader.read(
                                        InputStreamReader(fs.getPath(entry).inputStream()),
                                        notchNamespaceName,
                                        seargeNamespaceName,
                                        mappingTree
                                    )
                                }
                            }

                            MappingType.SRG_MERGED -> {
                                if (envType == EnvType.COMBINED) {
                                    SrgReader.read(
                                        InputStreamReader(fs.getPath(entry).inputStream()),
                                        notchNamespaceName,
                                        seargeNamespaceName,
                                        mappingTree
                                    )
                                }
                            }

                            MappingType.RGS_CLIENT -> {
                                if (envType == EnvType.CLIENT) {
                                    RGSReader.read(
                                        InputStreamReader(fs.getPath(entry).inputStream()),
                                        notchNamespaceName,
                                        seargeNamespaceName,
                                        mappingTree
                                    )
                                }
                            }

                            MappingType.RGS_SERVER -> {
                                if (envType == EnvType.SERVER) {
                                    RGSReader.read(
                                        InputStreamReader(fs.getPath(entry).inputStream()),
                                        notchNamespaceName,
                                        seargeNamespaceName,
                                        mappingTree
                                    )
                                }
                            }

                            MappingType.TSRG -> {
                                TsrgReader.read(
                                    InputStreamReader(fs.getPath(entry).inputStream()),
                                    notchNamespaceName,
                                    seargeNamespaceName,
                                    mappingTree
                                )
                            }

                            MappingType.TINY -> {
                                Tiny2Reader.read(InputStreamReader(fs.getPath(entry).inputStream()), mappingTree)
                            }
                        }
                        break
                    }
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

    fun createZipFS(zip: Path, mutable: Boolean = false): FileSystem {
        return FileSystems.newFileSystem(URI.create("jar:${zip.toUri()}"), mapOf("mutable" to mutable), null)
    }

    enum class MappingType(val pattern: Regex) {
        TINY(Regex("""(.+[/\\]|^)mappings.tiny$""")),
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

    }

    enum class MCPConfigVersion(val contains: Set<MappingType>,
        val doesntContain: Set<MappingType> = setOf(),
        val ignore: Set<MappingType> = setOf()
    ) {
        TINY_JAR(
            setOf(MappingType.TINY),
            setOf(MappingType.SRG_CLIENT, MappingType.SRG_SERVER, MappingType.SRG_MERGED, MappingType.TSRG, MappingType.RGS_CLIENT, MappingType.RGS_SERVER, MappingType.MCP_METHODS, MappingType.MCP_PARAMS, MappingType.MCP_FIELDS, MappingType.MCP_CLASSES)
        ),
        NEW_MCPCONFIG(
            setOf(MappingType.TSRG),
            setOf(MappingType.MCP_FIELDS, MappingType.MCP_METHODS, MappingType.MCP_PARAMS, MappingType.MCP_CLASSES, MappingType.RGS_SERVER, MappingType.RGS_CLIENT, MappingType.SRG_SERVER, MappingType.SRG_CLIENT, MappingType.SRG_MERGED)
        ),
        NEWFORGE_MCP(
            setOf(MappingType.MCP_METHODS, MappingType.MCP_PARAMS, MappingType.MCP_FIELDS),
            setOf(MappingType.MCP_CLASSES, MappingType.RGS_SERVER, MappingType.RGS_CLIENT, MappingType.SRG_SERVER, MappingType.SRG_CLIENT, MappingType.SRG_MERGED)
        ),
        MCP(
            setOf(MappingType.MCP_METHODS, MappingType.MCP_FIELDS, MappingType.SRG_CLIENT),
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