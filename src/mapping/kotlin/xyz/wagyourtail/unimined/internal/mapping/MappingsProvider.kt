package xyz.wagyourtail.unimined.internal.mapping

import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.*
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.IMappingProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.api.mapping.MemoryMapping
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.*
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

    private var freeze by FinalizeOnWrite(false)

    override var devNamespace: Namespace by FinalizeOnRead(LazyMutable {
        getNamespaces().values.firstOrNull { it.named } ?: throw IllegalStateException("No named namespace found in ${getNamespaces().keys}")
    })

    override var devFallbackNamespace: Namespace by FinalizeOnRead(LazyMutable {
        devNamespace.targets.firstOrNull { it != OFFICIAL } ?: if (devNamespace.targets.contains(OFFICIAL)) OFFICIAL else throw IllegalStateException("No fallback namespace found")
    })

    override val mappingsDeps = mutableMapOf<String, MappingDepConfig>()

    override fun removeKey(key: String) {
        if (freeze) throw IllegalStateException("Cannot remove key after freeze")
        mappingsDeps.remove(key)
    }


    override fun devNamespace(namespace: String) {
        val delegate: FinalizeOnRead<MappingNamespaceTree.Namespace> = MappingsProvider::class.getField("devNamespace")!!.getDelegate(this) as FinalizeOnRead<MappingNamespaceTree.Namespace>
        delegate.setValueIntl(LazyMutable { getNamespace(namespace) })
    }

    override fun devFallbackNamespace(namespace: String) {
        val delegate: FinalizeOnRead<MappingNamespaceTree.Namespace> = MappingsProvider::class.getField("devFallbackNamespace")!!.getDelegate(this) as FinalizeOnRead<MappingNamespaceTree.Namespace>
        delegate.setValueIntl(LazyMutable { getNamespace(namespace) })
    }

    override fun intermediary(key: String, action: MappingDepConfig.() -> Unit) {
        project.unimined.fabricMaven()
        mapping("net.fabricmc:intermediary:${minecraft.version}:v2", key) {
            outputs("intermediary", false) { listOf("official") }
            action()
        }
    }

    override fun legacyIntermediary(revision: Int, key: String, action: MappingDepConfig.() -> Unit) {
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
        mapping("${group}:intermediary:${minecraft.version}:v2", key) {
            outputs("intermediary", false) { listOf("official") }
            action()
        }
    }

    override fun babricIntermediary(key: String, action: MappingDepConfig.() -> Unit) {
        project.unimined.babricMaven()
        if (side == EnvType.COMBINED) throw IllegalStateException("Cannot use babricIntermediary with side COMBINED")
        mapping("babric:intermediary:${minecraft.version}:v2", key) {
            mapNamespace(side.classifier!!, "official")
            outputs("intermediary", false) { listOf("official") }
        }
    }

    override fun officialMappingsFromJar(key: String, action: MappingDepConfig.() -> Unit) {
        val mcFile = when (minecraft.side) {
            EnvType.CLIENT -> minecraft.minecraftData.minecraftClientFile
            EnvType.COMBINED -> minecraft.mergedOfficialMinecraftFile
            EnvType.SERVER, EnvType.DATAGEN -> minecraft.minecraftData.minecraftServerFile
        }
        mapping(project.files(mcFile), key) {
            outputs("official", false) { listOf() }
            action()
        }
    }

    override fun searge(version: String, key: String, action: MappingDepConfig.() -> Unit) {
        project.unimined.forgeMaven()
        val mappings = if (minecraft.minecraftData.mcVersionCompare(minecraft.version, "1.12.2") < 0) {
            "de.oceanlabs.mcp:mcp:${version}:srg@zip"
        } else {
            "de.oceanlabs.mcp:mcp_config:${version}@zip"
        }
        officialMappingsFromJar {
            action()
        }
        mapping(mappings, key) {
            mapNamespace("obf", "official")
            onlyExistingSrc()
            srgToSearge()
            outputs("searge", false) { listOf("official") }
            action()
        }
    }

    override fun hashed(key: String, action: MappingDepConfig.() -> Unit) {
        project.unimined.quiltMaven()
        mapping("org.quiltmc:hashed:${minecraft.version}", key) {
            outputs("hashed", false) { listOf("official") }
            action()
        }
    }

    override fun mojmap(key: String, action: MappingDepConfig.() -> Unit) {
        val mapping = when (minecraft.side) {
            EnvType.CLIENT, EnvType.COMBINED -> "client"
            EnvType.SERVER, EnvType.DATAGEN -> "server"
        }
        mapping("net.minecraft:$mapping-mappings:${minecraft.version}", key) {
            outputs("mojmap", true) {
                // check if we have searge or intermediary or hashed mappings
                val searge = if ("searge" in getNamespaces()) listOf("searge") else emptyList()
                val intermediary = if ("intermediary" in getNamespaces()) listOf("intermediary") else emptyList()
                val hashed = if ("hashed" in getNamespaces()) listOf("hashed") else emptyList()
                listOf("official") + intermediary + searge + hashed
            }
            action()
        }
    }

    override fun mcp(channel: String, version: String, key: String, action: MappingDepConfig.() -> Unit) {
        if (channel == "legacy") {
            project.unimined.wagYourMaven("releases")
            officialMappingsFromJar {
                action()
            }
        } else {
            project.unimined.forgeMaven()
        }
        mapping("de.oceanlabs.mcp:mcp_${channel}:${version}@zip", key) {
            if (channel == "legacy") {
                contains({ _, t ->
                    t != "MCP"
                }) {
                    onlyExistingSrc()
                    outputs("searge", false) { listOf("official") }
                }
                contains({ _, t ->
                    t == "MCP" || t == "OLDER_MCP"
                }) {
                    onlyExistingSrc()
                    srgToSearge()
                    outputs("mcp", true) { listOf("searge") }
                    sourceNamespace("searge")
                }
                contains({_, t ->
                    t == "OLD_MCP"
                }) {
                    onlyExistingSrc()
                    outputs("searge", false) { listOf("official") }
                    outputs("mcp", true) { listOf("searge") }
                }
                contains({f, t ->
                    t == "OLD_MCP" && !f.contains("classes")
                }) {
                    // make classes first
                    dependsOn("searge")
                }
            } else {
                onlyExistingSrc()
                srgToSearge()
                outputs("mcp", true) { listOf("searge") }
                sourceNamespace("searge")
            }
            action()
        }
    }

    override fun retroMCP(key: String, action: MappingDepConfig.() -> Unit) {
        project.unimined.mcphackersIvy()
        mapping("io.github.mcphackers:mcp:${minecraft.version}@zip", key) {
            if (minecraft.minecraftData.mcVersionCompare(minecraft.version, "1.3") < 0) {
                if (side == EnvType.COMBINED) throw IllegalStateException("Cannot use retroMCP with side COMBINED")
                mapNamespace(side.classifier!!, "official")
            }
            mapNamespace("named", "mcp")
            outputs("mcp", true) { listOf("official") }
            action()
        }
    }

    override fun yarn(build: Int, key: String, action: MappingDepConfig.() -> Unit) {
        project.unimined.fabricMaven()
        mapping("net.fabricmc:yarn:${minecraft.version}+build.${build}:v2", key) {
            outputs("yarn", true) { listOf("intermediary") }
            mapNamespace("named", "yarn")
            sourceNamespace("intermediary")
            action()
        }
    }

    override fun legacyYarn(build: Int, revision: Int, key: String, action: MappingDepConfig.() -> Unit) {
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
        mapping("${group}:yarn:${minecraft.version}+build.${build}:v2", "yarn") {
            outputs("yarn", true) { listOf("intermediary") }
            mapNamespace("named", "yarn")
            sourceNamespace("intermediary")
            action()
        }
    }

    override fun barn(build: Int, key: String, action: MappingDepConfig.() -> Unit) {
        project.unimined.babricMaven()
        mapping("babric:barn:${minecraft.version}+build.${build}:v2", "yarn") {
            outputs("barn", true) { listOf("intermediary") }
            mapNamespace("named", "barn")
            sourceNamespace("intermediary")
            action()
        }
    }

    override fun quilt(build: Int, classifier: String, key: String, action: MappingDepConfig.() -> Unit) {
        project.unimined.quiltMaven()
        mapping("org.quiltmc:quilt-mappings:${minecraft.version}+build.${build}:${classifier}", "quilt") {
            mapNamespace("named", "quilt")
            val intermediary = if (classifier.contains("intermediary")) listOf("intermediary") else emptyList()
            val hashed = if (intermediary.isEmpty()) listOf("hashed") else emptyList()
            outputs("quilt", true) {
                intermediary + hashed
            }
            sourceNamespace((intermediary + hashed).first())
            action()
        }
    }

    override fun freeze() {
        for ((key, dep) in mappingsDeps) {
            project.logger.info("[Unimined/MappingsProvider] Finalizing $key (${dep.dep})")
            (dep as MappingDepConfigImpl).finalize()
        }
        super.freeze()
    }

    override fun forgeBuiltinMCP(version: String, key: String, action: MappingDepConfig.() -> Unit) {
        project.unimined.forgeMaven()
        officialMappingsFromJar {
            action()
        }
        mapping("net.minecraftforge:forge:${minecraft.version}-${version}:src@zip", "mcp") {
            contains({ _, t ->
                !t.contains("MCP")
            }) {
                onlyExistingSrc()
                outputs("searge", false) { listOf("official") }
            }
            contains({ _, t ->
                t.contains("MCP")
            }) {
                outputs("mcp", true) { listOf("searge") }
                sourceNamespace("searge")
            }
            action()
        }
    }

    override fun mapping(dependency: Any, key: String, action: MappingDepConfig.() -> Unit) {
        if (freeze) {
            throw IllegalStateException("Cannot add mappings after mapping tree has been initialized")
        }
        if (project.logger.isDebugEnabled) {
            project.logger.debug("[Unimined/MappingsProvider] Adding $key -> $dependency")
            Thread.dumpStack()
        }

        mappingsDeps.computeIfAbsent(key) {
            project.dependencies.create(dependency).let { dep ->
                project.logger.info("[Unimined/MappingsProvider] Adding mapping dependency $dependency")
                MappingDepConfigImpl(dep, this)
            }
        }.apply(action)
    }

    private var _stub: MemoryMapping? = null

    override val stub: MemoryMapping
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

    fun getMojmapMappings(): MemoryMappingTree {
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
        if (!freeze) freeze = true

        if (mappingsDeps.isEmpty()) {
            project.logger.warn("[Unimined/MappingsProvider] No mappings specified!")
            return MemoryMappingTree()
        }

        project.logger.info("[Unimined/MappingsProvider] Loading mappings: \n    ${mappingsDeps.entries.joinToString("\n    ") { (it.key to it.value.dep.toString()).toString() }}")

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
                it.dependencies.addAll(mappingsDeps.values.map { it.dep })
            }
            val mappingBuilder = MappingTreeBuilder()
            mappingBuilder.side(minecraft.side)

            // parse each dep
            for (dep in mappingsDeps.values) {
                dep as MappingDepConfigImpl
                project.logger.info("[Unimined/MappingsProvider] Loading mappings from ${dep.dep.name}")
                // resolve dep to files, no pom
                val files = configuration.files(dep.dep).filter { it.extension != "pom" }

                // load each file
                project.logger.info("[Unimined/MappingsProvider] Loading mappings files ${files.joinToString(", ")}")

                for (file in files) {
                    if (minecraft.isMinecraftJar(file.toPath())) {
                        mappingBuilder.bytecodeJar(file.toPath(), dep.inputs)
                    } else {
                        mappingBuilder.mappingFile(file.toPath(), dep.inputs)
                    }
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
        if (!freeze) freeze = true
        val names = mappingsDeps.values.map { if (it.dep is FileCollectionDependency) (it.dep as FileCollectionDependency).files.first().nameWithoutExtension else "${it.dep.name}-${it.dep.version}" }.sorted() + (if (hasStubs) listOf("stub-${_stub!!.hash}") else listOf())
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
        var fixedClassName = toClassName
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
            val innerClassName = fixedClassName?.substringAfterLast('/')?.substringAfterLast('$') ?: fromClassName.substringAfterLast('$')
            if (outerToClassName != null && (fixedClassName == null || !fixedClassName.startsWith(outerToClassName))) {
                fixedClassName = "$outerToClassName$$innerClassName"
                project.logger.info(
                    "[Unimined/MappingsProvider] Detected missing inner class, replacing with: {} -> {}",
                    fromClassName,
                    fixedClassName
                )
            }
        }
        return fixedClassName
    }

    private open class Mapping(val to: String?)
    private class ClassMapping(val from: String, to: String): Mapping(to)
    private open class MemberMapping(val from: String, val fromDesc: String?, to: String): Mapping(to)
    private class MethodMapping(from: String, fromDesc: String, to: String): MemberMapping(from, fromDesc, to)
    private class FieldMapping(from: String, fromDesc: String?, to: String): MemberMapping(from, fromDesc, to)
    private class ArgumentMapping(to: String, val index: Int): Mapping(to)
    private class LocalVariableMapping(to: String, val lvIndex: Int, val startOpIdx: Int, val lvtRowIndex: Int): Mapping(to)

    private val mappingProvider: Map<Pair<Namespace, Namespace>, List<Mapping>> = defaultedMapOf { remap ->
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