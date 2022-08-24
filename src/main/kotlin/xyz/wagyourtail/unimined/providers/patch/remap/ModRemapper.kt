package xyz.wagyourtail.unimined.providers.patch.remap

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView
import net.fabricmc.tinyremapper.*
import net.fabricmc.tinyremapper.OutputConsumerPath.ResourceRemapper
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.maybeCreate
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// https://stackoverflow.com/questions/47947841/kotlin-var-lazy-init :)
class LazyMutable<T>(val initializer: () -> T) : ReadWriteProperty<Any?, T> {

    @Suppress("ClassName")
    private object UNINITIALIZED_VALUE
    private var prop: Any? = UNINITIALIZED_VALUE

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return if (prop == UNINITIALIZED_VALUE) {
            synchronized(this) {
                return if (prop == UNINITIALIZED_VALUE) initializer().also { prop = it } else prop as T
            }
        } else prop as T
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        synchronized(this) {
            prop = value
        }
    }
}

class ModRemapper(
    val project: Project,
    val mcRemapper: MinecraftRemapper
) {


    var fromMappings: String by LazyMutable { mcRemapper.fallbackTarget }
    var fallbackTo: String by LazyMutable { mcRemapper.fallbackTarget }
    var tinyRemapperConf: (TinyRemapper.Builder) -> Unit = {}

    private val combinedConfig = Configs(project, EnvType.COMBINED, this)
    private val clientConfig = Configs(project, EnvType.CLIENT, this)
    private val serverConfig = Configs(project, EnvType.SERVER, this)


    private val internalCombinedModRemapperConfiguration: Configuration = project.configurations.maybeCreate("internalModRemapper").apply {
        exclude(mapOf(
            "group" to "net.fabricmc",
            "module" to "fabric-loader"
        ))
    }

    fun internalModRemapperConfiguration(envType: EnvType): Configuration = when (envType) {
        EnvType.COMBINED -> internalCombinedModRemapperConfiguration
        EnvType.CLIENT -> project.configurations.maybeCreate("internalModRemapperClient").apply {
            extendsFrom(internalCombinedModRemapperConfiguration)
        }
        EnvType.SERVER -> project.configurations.maybeCreate("internalModRemapperServer").apply {
            extendsFrom(internalCombinedModRemapperConfiguration)
        }
    }

    init {
        project.repositories.forEach { repo ->
            repo.content {
                it.excludeGroupByRegex("remapped_.+")
            }
        }
        project.repositories.flatDir { repo ->
            repo.dirs(modTransformFolder().toAbsolutePath().toString())
            repo.content {
                it.includeGroupByRegex("remapped_.+")
            }
        }
    }

    private fun memberOf(className: String, memberName: String, descriptor: String?): IMappingProvider.Member {
        return IMappingProvider.Member(className, memberName, descriptor)
    }

    @ApiStatus.Internal
    fun getMappingProvider(
        srcName: String,
        fallbackSrc: String,
        targetName: String,
        mappingTree: MappingTreeView,
        remapLocalVariables: Boolean = true,
    ): (IMappingProvider.MappingAcceptor) -> Unit {
        return { acceptor: IMappingProvider.MappingAcceptor ->
            val fromId = mappingTree.getNamespaceId(srcName)
            var fallbackId = mappingTree.getNamespaceId(fallbackSrc)
            val toId = mappingTree.getNamespaceId(targetName)

            if (toId == MappingTreeView.NULL_NAMESPACE_ID) {
                throw RuntimeException("Namespace $targetName not found in mappings")
            }

            if (fallbackId == MappingTreeView.NULL_NAMESPACE_ID) {
                project.logger.warn("Namespace $fallbackSrc not found in mappings, using $targetName as fallback")
                fallbackId = toId
            }

            project.logger.debug("Mapping from $srcName fallback $fallbackId to $targetName")
            project.logger.debug("ids: from $fromId to $toId")

            for (classDef in mappingTree.classes) {
                val fromClassName = classDef.getName(fromId) ?: classDef.getName(fallbackId)
                val toClassName = classDef.getName(toId) ?: classDef.getName(fallbackId) ?: fromClassName

                if (fromClassName == null) {
                    project.logger.warn("No class name for $classDef")
                }
                project.logger.debug("fromClassName: $fromClassName toClassName: $toClassName")
                acceptor.acceptClass(fromClassName, toClassName)

                for (fieldDef in classDef.fields) {
                    val fromFieldName = fieldDef.getName(fromId) ?: fieldDef.getName(fallbackId)
                    val toFieldName = fieldDef.getName(toId) ?: fieldDef.getName(fallbackId) ?: fromFieldName
                    if (fromFieldName == null) {
                        project.logger.warn("No field name for $toFieldName")
                        continue
                    }
                    project.logger.debug("fromFieldName: $fromFieldName toFieldName: $toFieldName")
                    acceptor.acceptField(memberOf(fromClassName, fromFieldName, fieldDef.getDesc(fromId)), toFieldName)
                }

                for (methodDef in classDef.methods) {
                    val fromMethodName = methodDef.getName(fromId) ?: methodDef.getName(fallbackId)
                    val toMethodName = methodDef.getName(toId) ?: methodDef.getName(fallbackId) ?: fromMethodName
                    val fromMethodDesc = methodDef.getDesc(fromId) ?: methodDef.getDesc(fallbackId)
                    if (fromMethodName == null) {
                        project.logger.warn("No method name for $toMethodName")
                        continue
                    }
                    val method = memberOf(fromClassName, fromMethodName, fromMethodDesc)

                    project.logger.debug("fromMethodName: $fromMethodName toMethodName: $toMethodName")
                    acceptor.acceptMethod(method, toMethodName)

                    if (remapLocalVariables) {
                        for (arg in methodDef.args) {
                            val toArgName = arg.getName(toId) ?: arg.getName(fallbackId) ?: continue
                            acceptor.acceptMethodArg(method, arg.lvIndex, toArgName)
                        }

                        for (localVar in methodDef.vars) {
                            val toLocalVarName = localVar.getName(toId) ?: localVar.getName(fallbackId) ?: continue
                            acceptor.acceptMethodVar(
                                method,
                                localVar.lvIndex,
                                localVar.startOpIdx,
                                localVar.lvtRowIndex,
                                toLocalVarName
                            )
                        }
                    }
                }
            }
        }
    }


    fun remap(envType: EnvType) {
        val configs = when (envType) {
            EnvType.COMBINED -> combinedConfig
            EnvType.CLIENT -> clientConfig
            EnvType.SERVER -> serverConfig
        }
        configs.configurations.forEach {
            preTransform(configs.envType, it)
        }
        @Suppress("unused")
        val tr = TinyRemapper.newRemapper().withMappings(getMappingProvider(fromMappings, fallbackTo, mcRemapper.provider.targetNamespace.get(), mcRemapper.getMappingTree(configs.envType)))
            .renameInvalidLocals(true)
            .inferNameFromSameLvIndex(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .rebuildSourceFilenames(true)
        tinyRemapperConf(tr)
        tinyRemapper = tr.build()
        val mc = mcRemapper.provider.getMinecraftWithMapping(configs.envType, fromMappings)
        tinyRemapper.readClassPathAsync(mc)
        project.logger.warn("Remapping mods using $mc")
        tinyRemapper.readClassPathAsync(*mcRemapper.provider.mcLibraries.resolve().map { it.toPath() }.toTypedArray())
        configs.configurations.forEach {
            transform(configs.envType, it)
        }
        configs.configurations.forEach {
            postTransform(configs.envType, it)
        }
        tinyRemapper.finish()
    }

    private val dependencyMap = mutableMapOf<Configuration, MutableSet<Dependency>>()

    private fun preTransform(envType: EnvType, configuration: Configuration) {
        configuration.dependencies.forEach {
            internalModRemapperConfiguration(envType).dependencies.add(it)
            dependencyMap.computeIfAbsent(configuration) { mutableSetOf() } += (it)
        }
        configuration.dependencies.clear()
    }

    private fun transform(envType: EnvType, configuration: Configuration) {
        dependencyMap[configuration]?.forEach {
            transformMod(envType, it)
        }
    }

    private fun postTransform(envType: EnvType, configuration: Configuration) {
        dependencyMap[configuration]?.forEach {

            getOutputs(envType, it).forEach { artifact ->
                configuration.dependencies.add(
                    project.dependencies.create(
                        artifact
                    )
                )
            }
        }
    }

    private fun modTransformFolder(): Path {
        return mcRemapper.provider.parent.getLocalCache().resolve("modTransform").maybeCreate()

    }

    private lateinit var tinyRemapper: TinyRemapper
    private val outputMap = mutableMapOf<File, InputTag>()

    private fun transformMod(envType: EnvType, dependency: Dependency) {
        val files = internalModRemapperConfiguration(envType).files(dependency)
        for (file in files) {
            if (file.extension == "jar") {
                val targetTag = tinyRemapper.createInputTag()
                tinyRemapper.readInputs(targetTag, file.toPath())
                outputMap[file] = targetTag
            }
        }
    }

    private fun getOutputs(envType: EnvType, dependency: Dependency): Set<String> {
        val combinedNames = mcRemapper.getCombinedNames(envType)
        val outputs = mutableSetOf<String>()
        for (innerDep in internalModRemapperConfiguration(envType).resolvedConfiguration.getFirstLevelModuleDependencies { it == dependency }) {
            for (artifact in innerDep.allModuleArtifacts) {
                if (artifact.file.extension == "jar") {
                    val target = modTransformFolder().resolve("${artifact.file.nameWithoutExtension}-mapped-${combinedNames}-${mcRemapper.provider.targetNamespace.get()}.${artifact.file.extension}")
                    val classifier = artifact.classifier?.let { "$it-" } ?: ""
                    outputs += "remapped_${artifact.moduleVersion.id.group}:${artifact.name}:${artifact.moduleVersion.id.version}:${classifier}mapped-${combinedNames}-${mcRemapper.provider.targetNamespace.get()}"
                    if (target.exists()) {
                        continue
                    }
                    OutputConsumerPath.Builder(target).build().use {
                        it.addNonClassFiles(
                            artifact.file.toPath(),
                            tinyRemapper,
                            listOf(awRemapper(envType), innerJarStripper) + NonClassCopyMode.FIX_META_INF.remappers
                        )
                        tinyRemapper.apply(it, outputMap[artifact.file])
                    }
                } else {
                    outputs += artifact.id.toString()
                }
            }
        }
        return outputs
    }

    private fun awRemapper(envType: EnvType) : ResourceRemapper = object : ResourceRemapper {
        override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
            // read the beginning of the file and see if it begins with "accessWidener"
            return relativePath.extension.equals("accesswidener", true) ||
                   relativePath.extension.equals("aw", true)
        }

        override fun transform(
            destinationDirectory: Path,
            relativePath: Path,
            input: InputStream,
            remapper: TinyRemapper
        ) {
            val output = destinationDirectory.resolve(relativePath)
            output.parent.maybeCreate()
            val mappingTree = mcRemapper.getMappingTree(envType)
            BufferedReader(InputStreamReader(input)).use { reader ->
                BufferedWriter(OutputStreamWriter(BufferedOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))).use { writer ->
                    var line: List<String>? = reader.readLine().split("\\s+".toRegex())
                    // first line handling
                    if (line!![0] !="accessWidener") {
                        throw IllegalStateException("File does not start with accessWidener")
                    }
                    val mappingsFrom = mappingTree.getNamespaceId(line[2])
                    if (mappingsFrom == MappingTreeView.NULL_NAMESPACE_ID) {
                        throw IllegalStateException("Mappings from namespace ${line[2]} not found")
                    }
                    val mappingsTo = mappingTree.getNamespaceId(mcRemapper.provider.targetNamespace.get())
                    if (mappingsTo == MappingTreeView.NULL_NAMESPACE_ID) {
                        throw IllegalStateException("Mappings to namespace ${mcRemapper.provider.targetNamespace.get()} not found")
                    }
                    var fallbackTo = mappingTree.getNamespaceId(fromMappings)
                    if (fallbackTo == MappingTreeView.NULL_NAMESPACE_ID) {
                        fallbackTo = mappingsFrom
                    }
                    writer.write("accessWidener\t${line[1]}\t${mcRemapper.provider.targetNamespace.get()}\n")
                    line = reader.readLine()?.split("\\s+".toRegex())
                    while (line != null) {
                        val type = line[1]
                        writer.write("${line[0]}\t${type}\t")
                        val className = line[2]
                        val clazz = (mappingTree.getClass(className, mappingsFrom)  as ClassMappingView)
                        val newClassName = clazz.getDstName(mappingsTo) ?: clazz.getDstName(fallbackTo)
                        when (type) {
                            "class" -> {
                                writer.write("${newClassName}\n")
                            }
                            "method" -> {
                                val methodName = line[3]
                                val methodDesc = line[4]
                                val method = clazz.getMethod(methodName, methodDesc, mappingsFrom)
                                if (method != null) {
                                    val newMethodName = method.getDstName(mappingsTo) ?: method.getDstName(fallbackTo)
                                    val newMethodDesc = method.getDstDesc(mappingsTo) ?: method.getDstDesc(fallbackTo)
                                    writer.write("${newClassName}\t${newMethodName}\t${newMethodDesc}\n")
                                } else {
                                    project.logger.warn("Method ${methodName}${methodDesc} not found in class $className")
                                    writer.write("${newClassName}\t${methodName}\t${methodDesc}\n")
                                }
                            }
                            "field" -> {
                                val fieldName = line[3]
                                val fieldDesc = line[4]
                                val field = clazz.getField(fieldName, fieldDesc, mappingsFrom)
                                if (field != null) {
                                    val newFieldName = field.getDstName(mappingsTo) ?: field.getDstName(fallbackTo)
                                    val newFieldDesc = field.getDstDesc(mappingsTo) ?: field.getDstDesc(fallbackTo)
                                    writer.write("${newClassName}\t${newFieldName}\t${newFieldDesc}\n")
                                } else {
                                    project.logger.warn("Field ${fieldName}${fieldDesc} not found in class $className")
                                    writer.write("${newClassName}\t${fieldName}\t${fieldDesc}\n")
                                }
                            }
                            else -> {
                                throw IllegalStateException("Unknown type: $type")
                            }
                        }
                        line = reader.readLine()?.split("\\s+".toRegex())
                    }
                }
            }
        }
    }

    private val innerJarStripper : ResourceRemapper = object : ResourceRemapper {
        override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
            return relativePath.name.contains(".mod.json")
        }

        override fun transform(
            destinationDirectory: Path,
            relativePath: Path,
            input: InputStream,
            remapper: TinyRemapper
        ) {
            val output = destinationDirectory.resolve(relativePath)
            output.parent.maybeCreate()
            BufferedReader(InputStreamReader(input)).use { reader ->
                val json = JsonParser.parseReader(reader)
                json.asJsonObject.remove("jars")
                json.asJsonObject.remove("quilt_loader")
                BufferedWriter(OutputStreamWriter(BufferedOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))).use { writer ->
                    GsonBuilder().setPrettyPrinting().create().toJson(json, writer)
                }
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    data class Configs(val project: Project, val envType: EnvType, val parent: ModRemapper) {
        val configurations = mutableSetOf<Configuration>()
        private val envTypeName  = envType.classifier?.capitalized() ?: ""

        private fun registerConfiguration(configuration: Configuration): Configuration {
            configurations += configuration
            return configuration
        }

        val modCompileOnly: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modCompileOnly$envTypeName")
                .apply {
                    extendsFrom(project.configurations.getByName("compileOnly"))
                    exclude(mapOf(
                        "group" to "net.fabricmc",
                        "module" to "fabric-loader"
                    ))
                })

        val modRuntimeOnly: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modRuntimeOnly$envTypeName")
                .apply {
                    extendsFrom(project.configurations.getByName("runtimeOnly"))
                    exclude(mapOf(
                        "group" to "net.fabricmc",
                        "module" to "fabric-loader"
                    ))
                })

        val localRuntime: Configuration = project.configurations.maybeCreate("localRuntime$envTypeName").apply {
            extendsFrom(project.configurations.getByName("runtimeOnly"))
            exclude(mapOf(
                "group" to "net.fabricmc",
                "module" to "fabric-loader"
            ))
        }

        val modLocalRuntime: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modLocalRuntime" + envTypeName)
                .apply {
                    extendsFrom(project.configurations.getByName("localRuntime"))
                    exclude(mapOf(
                        "group" to "net.fabricmc",
                        "module" to "fabric-loader"
                    ))
                })

        val modImplementation: Configuration = registerConfiguration(
            project.configurations.maybeCreate("modImplementation" + envTypeName)
                .apply {
                    extendsFrom(project.configurations.getByName("implementation"))
                    exclude(mapOf(
                        "group" to "net.fabricmc",
                        "module" to "fabric-loader"
                    ))
                })


        init {
            parent.mcRemapper.provider.parent.events.register(::sourceSets)
        }

        private fun sourceSets(sourceSets: SourceSetContainer) {
            when (envType) {
                EnvType.SERVER -> {
                    sourceSets.findByName("server")?.apply {
                        compileClasspath += modCompileOnly + modImplementation
                        runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                }
                EnvType.CLIENT -> {
                    sourceSets.findByName("client")?.apply {
                        compileClasspath += modCompileOnly + modImplementation
                        runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                }
                EnvType.COMBINED -> {
                    sourceSets.findByName("main")?.apply {
                        compileClasspath += modCompileOnly + modImplementation
                        runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                    sourceSets.findByName("server")?.apply {
                        compileClasspath += modCompileOnly + modImplementation
                        runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                    sourceSets.findByName("client")?.apply {
                        compileClasspath += modCompileOnly + modImplementation
                        runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
                    }
                }
            }
        }
    }
}
