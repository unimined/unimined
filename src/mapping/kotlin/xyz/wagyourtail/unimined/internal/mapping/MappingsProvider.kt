package xyz.wagyourtail.unimined.internal.mapping

import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.*
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.api.mappings.MemoryMapping
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.toHex
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.reader

class MappingsProvider(project: Project, minecraft: MinecraftConfig): MappingsConfig(project, minecraft) {

    override var side: EnvType
        get() = minecraft.side
        set(value) {
            minecraft.side = value
        }

    private var freeze = false

    override var devNamespace by FinalizeOnRead(LazyMutable {
        available.firstOrNull { it.type == MappingNamespace.Type.NAMED } ?: throw IllegalStateException("No named namespace available")
    })

    override var devFallbackNamespace by FinalizeOnRead(LazyMutable {
        available.firstOrNull { it.type == MappingNamespace.Type.INT } ?: throw IllegalStateException("No int namespace available")
    })

    override val mappingsDeps = mutableListOf<MappingDepConfig<*>>()

    override val available: Set<MappingNamespace> by lazy {
        (mappingTree.dstNamespaces.filter { it != "srg" } + mappingTree.srcNamespace).map { MappingNamespace.getNamespace(it) }.toSet()
    }

    override fun mapping(dependency: Any, action: MappingDepConfig<*>.() -> Unit) {
        if (mappingTreeLazy.isInitialized()) {
            throw IllegalStateException("Cannot add mappings after mapping tree has been initialized")
        }
        project.dependencies.create(dependency).let {
            mappingsDeps.add(
                if (it is ExternalModuleDependency) {
                    ExternalMappingDepImpl(it, this).also(action)
                } else {
                    MappingDepImpl(it, this).also(action)
                }
            )
        }
    }

    private var _stub: MemoryMapping? = null

    val stub: MemoryMapping
        get() {
            if (freeze) {
                throw IllegalStateException("Cannot access stub after mapping tree has been initialized")
            }
            if (_stub == null) {
                _stub = MemoryMapping()
            }
            return _stub!!
        }

    override val hasStubs: Boolean
        get() =_stub != null

    private fun getOfficialMappings(): MemoryMappingTree {
        val off = project.configurations.detachedConfiguration(
            project.dependencies.create(
                "net.minecraft:minecraft:${minecraft.version}:client-mappings"
            )
        )
        val file = off.resolve().first { it.extension == "txt" }
        val tree = MemoryMappingTree()
        file.inputStream()
            .use {
                ProGuardReader.read(
                    it.reader(), MappingNamespace.MOJMAP.namespace, MappingNamespace.OFFICIAL.namespace,
                    MappingSourceNsSwitch(tree, MappingNamespace.OFFICIAL.namespace)
                )
            }
        return tree
    }

    private fun writeToCache(file: Path, mappingTree: MappingTreeView) {
        file.parent.createDirectories()
        ZipOutputStream(file.outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)).use {
            it.putNextEntry(ZipEntry("mappings/mappings.tiny"))
            OutputStreamWriter(it).let { writer ->
                mappingTree.accept(Tiny2Writer2(writer, false))
                writer.flush()
            }
            it.closeEntry()
        }
    }

    private val mappingTreeLazy = lazy {
        project.logger.lifecycle("[Unimined/MappingsProvider] Resolving mappings for ${minecraft.sourceSet}")
        val mappings = MemoryMappingTree()
        freeze = true

        // if has cache and not force reload
        val cacheFile = mappingCacheFile()
        if (cacheFile.exists() && !project.unimined.forceReload) {
            project.logger.info("[Unimined/MappingsProvider] Loading mappings from cache")
            // load from cache
            cacheFile.reader().use {
                Tiny2Reader2.read(it, mappings)
            }
        } else {
            val configuration = project.configurations.detachedConfiguration(*mappingsDeps.toTypedArray())
            configuration.resolve()

            // parse each dep
            for (dep in mappingsDeps) {
                project.logger.info("[Unimined/MappingsProvider] Loading mappings from ${dep.name}")
                // resolve dep to files, no pom
                val files = configuration.files(dep).filter { it.extension != "pom" }
                // load each file
                for (file in files) {
                    // detect if file is archive by seeing if it starts with the zip header
                    if (file.inputStream().use { stream -> ByteArray(4).also { stream.read(it, 0, 4) } }
                            .contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))) {
                        // load from archive
                        val contents = ZipReader.readContents(file.toPath())
                        val type = ZipReader.getZipTypeFromContentList(contents)
                        project.logger.info("[Unimined/MappingsProvider] Loading mappings from ${file.name} as $type")
                        if (type == ZipReader.ZipFormat.TINY_JAR) {
                            // if user hasn't specified what named should be, default to yarn
                            if (dep.mapNamespace["named"] == null) {
                                dep.mapNamespace["named"] = MappingNamespace.YARN
                            }
                        }
                        ZipReader.readMappings(dep.side, file.toPath(), contents, mappings, dep.mapNamespace)
                    } else {
                        // load from file
                        val format = MappingReader.detectFormat(file.toPath())

                        if (format == MappingFormat.PROGUARD) {
                            // detect if source/target are overwritten
                            if (dep.mapNamespace[MappingUtil.NS_SOURCE_FALLBACK] == null) {
                                dep.mapNamespace[MappingUtil.NS_SOURCE_FALLBACK] = MappingNamespace.MOJMAP
                            }
                            if (dep.mapNamespace[MappingUtil.NS_TARGET_FALLBACK] == null) {
                                dep.mapNamespace[MappingUtil.NS_TARGET_FALLBACK] = MappingNamespace.OFFICIAL
                            }
                            if (dep.mapNamespace[MappingUtil.NS_TARGET_FALLBACK] == MappingNamespace.OFFICIAL) {
                                file.reader().use {
                                    ProGuardReader.read(it, MappingNsRenamer(
                                            MappingSourceNsSwitch(
                                                mappings,
                                                MappingNamespace.OFFICIAL.namespace
                                            ), dep.mapNamespace.mapValues { it.value.namespace })
                                    )
                                }
                            } else {
                                MappingReader.read(file.toPath(), format, MappingNsRenamer(mappings, dep.mapNamespace.mapValues { it.value.namespace }))
                            }
                        } else {
                            MappingReader.read(file.toPath(), format, MappingNsRenamer(mappings, dep.mapNamespace.mapValues { it.value.namespace }))
                        }
                    }
                }
            }
            if (mappingTree.dstNamespaces.contains("srg")) {
                project.logger.info("[Unimined/MappingsProvider] Detected TSRG2 mappings (1.17+) - converting to have the right class names for runtime forge")
                if (!mappingTree.dstNamespaces.contains(MappingNamespace.MOJMAP.namespace)) {
                    getOfficialMappings().accept(mappings)
                }
                SeargeFromTsrg2.apply("srg", MappingNamespace.MOJMAP.namespace, MappingNamespace.SEARGE.namespace, mappings)
            }
            if (hasStubs) {
                project.logger.info("[Unimined/MappingsProvider] Loading stub mappings")
                _stub!!.visit(mappings)
            }
            writeToCache(cacheFile, mappingTree)
        }

        project.logger.lifecycle("[Unimined/MappingsProvider] Mapping tree initialized, ${mappingTree.srcNamespace} -> ${mappingTree.dstNamespaces.filter { it != "srg" }}")
        mappings
    }

    val mappingTree: MappingTreeView
        get() = mappingTreeLazy.value

    private fun mappingCacheFile(): Path =
        (if (hasStubs) project.unimined.getLocalCache() else project.unimined.getGlobalCache())
            .resolve("mappings-${side}-${combinedNames}.jar")


    override val combinedNames: String by lazy {
        freeze = true
        val names = mappingsDeps.map { "${it.name}-${it.version}" }.sorted() + (if (hasStubs) listOf("stub-${_stub!!.hash}") else listOf())
        names.joinToString("-")
    }

    fun MappingTreeView.sha256() {
        val sha = MessageDigest.getInstance("SHA-256")
        val stringWriter = StringWriter()
        this.accept(Tiny2Writer2(stringWriter, false))
        sha.update(stringWriter.toString().toByteArray())
        sha.digest().toHex().substring(0..8)
    }

    private fun memberOf(className: String, memberName: String, descriptor: String?): IMappingProvider.Member {
        return IMappingProvider.Member(className, memberName, descriptor)
    }

    private fun fixInnerClassName(
        mappings: MappingTreeView,
        fromId: Int,
        toId: Int,
        fromClassName: String,
        toClassName: String?
    ): String? {
        var toClassName = toClassName
        val outerClass = fromClassName.substring(0, fromClassName.lastIndexOf('$'))
        val outerClassDef = mappings.getClass(outerClass, fromId)
        if (outerClassDef != null) {
            val outerFromClassName = outerClassDef.getName(fromId)
            var outerToClassName = outerClassDef.getName(toId)
            if (outerFromClassName != null && outerFromClassName.contains('$')) {
                outerToClassName = fixInnerClassName(
                    mappings,
                    fromId,
                    toId,
                    outerFromClassName,
                    outerToClassName
                )
            }
            val innerClassName = toClassName?.substringAfterLast('/')?.substringAfterLast('$') ?: fromClassName.substringAfterLast('$')
            if (outerToClassName != null && (toClassName == null || !toClassName.startsWith(outerToClassName))) {
                toClassName = "$outerToClassName$$innerClassName"
                project.logger.info(
                    "[Unimined/MappingsProvider] Detected missing inner class, replacing with: {} -> {}",
                    fromClassName,
                    toClassName
                )
            }
        }
        return toClassName
    }

    private open class Mapping(val to: String?)
    private class ClassMapping(val from: String, to: String): Mapping(to)
    private open class MemberMapping(val from: String, val fromDesc: String?, to: String): Mapping(to)
    private class MethodMapping(from: String, fromDesc: String, to: String): MemberMapping(from, fromDesc, to)
    private class FieldMapping(from: String, fromDesc: String?, to: String): MemberMapping(from, fromDesc, to)
    private class ArgumentMapping(to: String, val index: Int): Mapping(to)
    private class LocalVariableMapping(to: String, val lvIndex: Int, val startOpIdx: Int, val lvtRowIndex: Int): Mapping(to)

    private val mappingProvider: Map<Pair<MappingNamespace, MappingNamespace>, List<Mapping>> = defaultedMapOf { remap ->
        project.logger.info("[Unimined/MappingsProvider] resolving internal mappings provider for $remap in ${minecraft.sourceSet}")
        val reverse = remap.first.shouldReverse(remap.second)
        val srcName = (if (reverse) remap.second else remap.first).namespace
        val dstName = (if (reverse) remap.first else remap.second).namespace

        val fromId = mappingTree.getNamespaceId(srcName)
        val toId = mappingTree.getNamespaceId(dstName)

        if (fromId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalArgumentException("Unknown source namespace: $srcName")
        }

        if (toId == MappingTreeView.NULL_NAMESPACE_ID) {
            throw IllegalArgumentException("Unknown target namespace: $dstName")
        }

        val mappings = mutableListOf<Mapping>()

        for (classDef in mappingTree.classes) {
            var fromClassName = classDef.getName(fromId)
            var toClassName = classDef.getName(toId)

            if (fromClassName == null) {
                project.logger.info("Target class {} has no name in namespace {}", classDef, srcName)
                fromClassName = toClassName
            }

            // detect missing inner class
            if (fromClassName != null && fromClassName.contains("$")) {
                toClassName = fixInnerClassName(
                    mappingTree,
                    fromId,
                    toId,
                    fromClassName,
                    toClassName
                )
            }

            if (toClassName == null) {
                project.logger.info("Target class {} has no name in namespace {}", classDef, dstName)
                toClassName = fromClassName
            }

            if (fromClassName == null) {
                project.logger.info("Class $classDef has no name in either namespace $srcName or $dstName")
                continue
            }

            if (reverse) {
                mappings.add(ClassMapping(toClassName, fromClassName))
            } else {
                mappings.add(ClassMapping(fromClassName, toClassName))
            }

            for (fieldDef in classDef.fields) {
                val fromFieldName = fieldDef.getName(fromId)
                val toFieldName = fieldDef.getName(toId)

                if (fromFieldName == null) {
                    project.logger.info("Target field {} has no name in namespace {}", fieldDef, srcName)
                    continue
                }

                if (toFieldName == null) {
                    project.logger.info("Target field {} has no name in namespace {}", fieldDef, dstName)
                    continue
                }

                if (reverse) {
                    mappings.add(FieldMapping(toFieldName, fieldDef.getDesc(toId), fromFieldName))
                } else {
                    mappings.add(FieldMapping(fromFieldName, fieldDef.getDesc(fromId), toFieldName))
                }
            }

            for (methodDef in classDef.methods) {
                val fromMethodName = methodDef.getName(fromId)
                val toMethodName = methodDef.getName(toId)

                if (fromMethodName == null) {
                    project.logger.info("Target method {} has no name in namespace {}", methodDef, srcName)
                    continue
                }

                if (toMethodName == null) {
                    project.logger.info("Target method {} has no name in namespace {}", methodDef, dstName)
                    continue
                }

                if (reverse) {
                    mappings.add(MethodMapping(toMethodName, methodDef.getDesc(toId)!!, fromMethodName))
                } else {
                    mappings.add(MethodMapping(fromMethodName, methodDef.getDesc(fromId)!!, toMethodName))
                }

                for (arg in methodDef.args) {
                    val toArgName = if (reverse) arg.getName(fromId) else arg.getName(toId)

                    if (toArgName != null) {
                        mappings.add(ArgumentMapping(toArgName, arg.lvIndex))
                    }
                }

                for (localVar in methodDef.vars) {
                    val toLocalVarName = if (reverse) localVar.getName(fromId) else localVar.getName(toId)

                    if (toLocalVarName != null) {
                        mappings.add(
                            LocalVariableMapping(
                                toLocalVarName,
                                localVar.lvIndex,
                                localVar.startOpIdx,
                                localVar.lvtRowIndex
                            )
                        )
                    }
                }

            }
        }

        mappings
    }

    override fun getTRMappings(
        remap: Pair<MappingNamespace, MappingNamespace>,
        remapLocals: Boolean,
    ) : (IMappingProvider.MappingAcceptor) -> Unit {
        val mappings = mappingProvider[remap] ?: throw IllegalStateException("mapping provider returned null for $remap, this should never happen!")
        return { acceptor ->
            var lastClass: String? = null
            var lastMethod: IMappingProvider.Member? = null
            for (mapping in mappings) {
                when (mapping) {
                    is ClassMapping -> {
                        lastClass = mapping.from
                        lastMethod = null
                        acceptor.acceptClass(mapping.from, mapping.to)
                    }

                    is MethodMapping -> {
                        if (lastClass == null) throw IllegalStateException("Method mapping before class mapping")
                        lastMethod = memberOf(lastClass, mapping.from, mapping.fromDesc)

                        acceptor.acceptMethod(lastMethod, mapping.to)
                    }

                    is FieldMapping -> {
                        if (lastClass == null) throw IllegalStateException("Field mapping before class mapping")
                        lastMethod = null
                        acceptor.acceptField(memberOf(lastClass, mapping.from, mapping.fromDesc), mapping.to)
                    }

                    is ArgumentMapping -> {
                        if (lastMethod == null) throw IllegalStateException("Argument mapping before method mapping")
                        if (!remapLocals) continue
                        acceptor.acceptMethodArg(lastMethod, mapping.index, mapping.to)
                    }

                    is LocalVariableMapping -> {
                        if (lastMethod == null) throw IllegalStateException("Local variable mapping before method mapping")
                        if (!remapLocals) continue
                        acceptor.acceptMethodVar(
                            lastMethod,
                            mapping.lvIndex,
                            mapping.startOpIdx,
                            mapping.lvtRowIndex,
                            mapping.to
                        )
                    }
                }
            }
        }
    }
}