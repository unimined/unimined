package xyz.wagyourtail.unimined.internal.mapping

import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.*
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.api.mapping.MemoryMapping
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.LazyMutable
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.toHex
import java.io.IOException
import java.io.StringWriter
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.*

class MappingsProvider(project: Project, minecraft: MinecraftConfig): MappingsConfig(project, minecraft) {

    override var side: EnvType
        get() = minecraft.side
        set(value) {
            minecraft.side = value
        }

    private var freeze = false

    override var devNamespace: Namespace by FinalizeOnRead(LazyMutable {
        getNamespaces().values.first { it.named }
    })

    override var devFallbackNamespace: Namespace by FinalizeOnRead(LazyMutable {
        devNamespace.targets.firstOrNull { it != OFFICIAL } ?: if (devNamespace.targets.contains(OFFICIAL)) OFFICIAL else throw IllegalStateException("No fallback namespace found")
    })

    override val mappingsDeps = mutableListOf<MappingDepConfig<*>>()


    override fun intermediary(action: MappingDepConfig<*>.() -> Unit) {
        mapping("net.fabricmc:intermediary:${minecraft.version}:v2", action)
    }

    override fun legacyIntermediary(revision: Int, action: MappingDepConfig<*>.() -> Unit) {
        if (legacyFabricMappingsVersionFinalize.value != revision) {
            if (!legacyFabricMappingsVersionFinalize.finalized) {
                legacyFabricMappingsVersion = revision
                legacyFabricMappingsVersionFinalize.finalized = true
            } else {
                project.logger.warn("[Unimined/MappingsProvider] Different revisions of legacy fabric mappings were used. This will most likely cause issues.")
            }
        }
        val group = if (revision < 2) {
            "net.legacyfabric"
        } else {
            "net.legacyfabric.v${revision}"
        }
        mapping("${group}:intermediary:${minecraft.version}:v2",action)
    }

    override fun babricIntermediary(action: MappingDepConfig<*>.() -> Unit) {
        mapping("babric:intermediary:${minecraft.version}:v2", action)
    }

    override fun searge(version: String, action: MappingDepConfig<*>.() -> Unit) {
        val mappings = if (minecraft.minecraftData.mcVersionCompare(minecraft.version, "1.12.2") < 0) {
            "de.oceanlabs.mcp:mcp:${version}:srg@zip"
        } else {
            "de.oceanlabs.mcp:mcp_config:${version}@zip"
        }
        mapping(mappings, action)
    }

    override fun hashed(action: MappingDepConfig<*>.() -> Unit) {
        mapping("org.quiltmc:hashed:${minecraft.version}") {
            action()
        }
    }

    override fun mojmap(action: MappingDepConfig<*>.() -> Unit) {
        val mapping = when (minecraft.side) {
            EnvType.CLIENT, EnvType.COMBINED -> "client"
            EnvType.SERVER, EnvType.DATAGEN -> "server"
        }
        mapping("net.minecraft:$mapping-mappings:${minecraft.version}", action)
    }

    override fun mcp(channel: String, version: String, action: MappingDepConfig<*>.() -> Unit) {
        if (channel == "legacy") {
            project.unimined.wagYourMaven("releases")
        } else {
            project.unimined.forgeMaven()
        }
        mapping("de.oceanlabs.mcp:mcp_${channel}:${version}@zip",action)
    }

    override fun retroMCP(action: MappingDepConfig<*>.() -> Unit) {
        project.unimined.mcphackersIvy()
        mapping("io.github.mcphackers:mcp:${minecraft.version}@zip") {
            action()
        }
    }

    override fun yarn(build: Int, action: MappingDepConfig<*>.() -> Unit) {
        project.unimined.fabricMaven()
        mapping("net.fabricmc:yarn:${minecraft.version}+build.${build}:v2") {
            action()
        }
    }

    override fun legacyYarn(build: Int, revision: Int, action: MappingDepConfig<*>.() -> Unit) {
        project.unimined.legacyFabricMaven()
        if (legacyFabricMappingsVersionFinalize.value != revision) {
            if (!legacyFabricMappingsVersionFinalize.finalized) {
                legacyFabricMappingsVersion = revision
                legacyFabricMappingsVersionFinalize.finalized = true
            } else {
                project.logger.warn("[Unimined/MappingsProvider] Different revisions of legacy fabric mappings were used. This will most likely cause issues.")
            }
        }
        val group = if (revision < 2) {
            "net.legacyfabric"
        } else {
            "net.legacyfabric.v${revision}"
        }
        mapping("${group}:yarn:${minecraft.version}+build.${build}:v2") {
            action()
        }
    }

    override fun barn(build: Int, action: MappingDepConfig<*>.() -> Unit) {
        mapping("babric:barn:${minecraft.version}+build.${build}:v2") {
            action()
        }
    }

    override fun quilt(build: Int, classifier: String, action: MappingDepConfig<*>.() -> Unit) {
        mapping("org.quiltmc:quilt-mappings:${minecraft.version}+build.${build}:${classifier}") {
            action()
        }
    }

    override fun forgeBuiltinMCP(version: String, action: MappingDepConfig<*>.() -> Unit) {
        project.unimined.forgeMaven()
        mapping("net.minecraftforge:forge:${minecraft.version}-${version}:src@zip", action)
    }

    override fun mapping(dependency: Any, action: MappingDepConfig<*>.() -> Unit) {
        if (freeze) {
            throw IllegalStateException("Cannot add mappings after mapping tree has been initialized")
        }
        project.dependencies.create(dependency).let {
            mappingsDeps.add(
                if (it is ExternalModuleDependency) {
                    ExternalMappingDepImpl(it, this).also(action)
                } else {
                    MappingDepImpl(it, this).also(action)
                }.also {
                    if (it.inputs.nsFilter.isEmpty()) {
                        throw IllegalStateException("No namespaces specified for mapping dependency $it")
                    }
                    //TODO: other checks
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
        val tree = MemoryMappingTree()
        when (minecraft.side) {
            EnvType.COMBINED, EnvType.CLIENT -> minecraft.minecraftData.officialClientMappingsFile
            EnvType.SERVER, EnvType.DATAGEN -> minecraft.minecraftData.officialServerMappingsFile
        }.inputStream().use {
            ProGuardReader.read(
                it.reader(), "mojmap", "official",
                MappingSourceNsSwitch(tree, "official")
            )
        }
        return tree
    }

    private fun resolveMappingTree(): MappingTreeView {
        project.logger.lifecycle("[Unimined/MappingsProvider] Resolving mappings for ${minecraft.sourceSet}")
        lateinit var mappings: MappingTreeView
        freeze = true

        if (mappingsDeps.isEmpty()) {
            project.logger.warn("[Unimined/MappingsProvider] No mappings specified!")
            return MemoryMappingTree()
        }

        project.logger.info("[Unimined/MappingsProvider] Loading mappings: \n    ${mappingsDeps.joinToString("\n    ") { it.dep.toString() }}")

        // if has cache and not force reload
        val cacheFile = mappingCacheFile()
        cacheFile.parent.createDirectories()
        val loaded = if (cacheFile.exists() && !project.unimined.forceReload) {
            project.logger.info("[Unimined/MappingsProvider] Loading mappings from cache")
            // load from cache
            try {
                cacheFile.reader().use {
                    MemoryMappingTree().also { map ->
                        Tiny2Reader2.read(it, map)
                        mappings = map
                    }
                }
                true
            } catch (e: IOException) {
                project.logger.warn("[Unimined/MappingsProvider] Failed to load mappings from cache, reloading from deps ${e.message}")
                // delete cache
                cacheFile.deleteExisting()
                false
            }
        } else false
        if (!loaded) {
            val configuration = project.configurations.detachedConfiguration().also {
                it.dependencies.addAll(mappingsDeps)
            }
            val mappingBuilder = MappingTreeBuilder()

            // parse each dep
            for (dep in mappingsDeps) {
                project.logger.info("[Unimined/MappingsProvider] Loading mappings from ${dep.name}")
                // resolve dep to files, no pom
                val files = configuration.files(dep).filter { it.extension != "pom" }
                // load each file
                project.logger.info("[Unimined/MappingsProvider] Loading mappings files ${files.joinToString(", ")}")

                val input = (dep as MappingDepConfigImpl<*>).inputs
                val prev = input.fmv
                input.forwardVisitor {
                    SrgToSeargeMapper(prev(it), "srg", "searge", "mojmap", ::getOfficialMappings)
                }

                for (file in files) {
                    mappingBuilder.mappingFile(file.toPath(), input)

                }
            }
            mappings = mappingBuilder.build()
            if (hasStubs) {
                project.logger.info("[Unimined/MappingsProvider] Loading stub mappings")
                _stub!!.visit(mappings as MappingVisitor)
            }
            cacheFile.bufferedWriter().use {
                mappings.accept(Tiny2Writer2(it, false))
            }
        }

        project.logger.lifecycle("[Unimined/MappingsProvider] Mapping tree initialized, ${mappings.srcNamespace} -> ${mappings.dstNamespaces.filter { it != "srg" }}")
        return mappings
    }

    val mappingTree: MappingTreeView by lazy {
        resolveMappingTree()
    }

    private fun mappingCacheFile(): Path =
        (if (hasStubs) project.unimined.getLocalCache() else project.unimined.getGlobalCache())
            .resolve("mappings").resolve("mappings-${side}-${combinedNames}.tiny")


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

    private val mappingProvider: Map<Pair<MappingNamespaceTree.Namespace, MappingNamespaceTree.Namespace>, List<Mapping>> = defaultedMapOf { remap ->
        project.logger.info("[Unimined/MappingsProvider] resolving internal mappings provider for $remap in ${minecraft.sourceSet}")
        val srcName = remap.first
        val dstName = remap.second

        val fromId = mappingTree.getNamespaceId(srcName.name)
        val toId = mappingTree.getNamespaceId(dstName.name)

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
                project.logger.debug("[Unimined/MappingsProvider] Target class {} has no name in namespace {}", classDef, srcName)
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
                project.logger.debug("[Unimined/MappingsProvider] Target class {} has no name in namespace {}", classDef, dstName)
                toClassName = fromClassName
            }

            if (fromClassName == null) {
                project.logger.debug("[Unimined/MappingsProvider] Class $classDef has no name in either namespace $srcName or $dstName")
                continue
            }

            mappings.add(ClassMapping(fromClassName, toClassName))

            for (fieldDef in classDef.fields) {
                val fromFieldName = fieldDef.getName(fromId)
                val toFieldName = fieldDef.getName(toId)

                if (fromFieldName == null) {
                    project.logger.debug("[Unimined/MappingsProvider] Target field {} has no name in namespace {}", fieldDef, srcName)
                    continue
                }

                if (toFieldName == null) {
                    project.logger.debug("[Unimined/MappingsProvider] Target field {} has no name in namespace {}", fieldDef, dstName)
                    continue
                }

                mappings.add(FieldMapping(fromFieldName, fieldDef.getDesc(fromId), toFieldName))
            }

            for (methodDef in classDef.methods) {
                val fromMethodName = methodDef.getName(fromId)
                val toMethodName = methodDef.getName(toId)

                if (fromMethodName == null) {
                    project.logger.debug("[Unimined/MappingsProvider] Target method {} has no name in namespace {}", methodDef, srcName)
                    continue
                }

                if (toMethodName == null) {
                    project.logger.debug("[Unimined/MappingsProvider] Target method {} has no name in namespace {}", methodDef, dstName)
                    continue
                }

                mappings.add(MethodMapping(fromMethodName, methodDef.getDesc(fromId)!!, toMethodName))

                for (arg in methodDef.args) {
                    val toArgName = arg.getName(toId)

                    if (toArgName != null) {
                        mappings.add(ArgumentMapping(toArgName, arg.lvIndex))
                    }
                }

                for (localVar in methodDef.vars) {
                    val toLocalVarName = localVar.getName(toId)

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
        remap: Pair<Namespace, Namespace>,
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