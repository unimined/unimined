package xyz.wagyourtail.unimined.providers.patch.remap

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.mappingio.tree.MappingTreeView
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView
import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.NonClassCopyMode
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.OutputConsumerPath.ResourceRemapper
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.maybeCreate
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name

class ModRemapper(
    val project: Project,
    val mcRemapper: MinecraftRemapper
) {

    private val configurations = mutableSetOf<Configuration>()

    val modCompileOnly: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modCompileOnly")
            .apply {
                extendsFrom(project.configurations.getByName("compileOnly"))
                exclude(mapOf(
                    "group" to "net.fabricmc",
                    "module" to "fabric-loader"
                ))
            })

    val modRuntimeOnly: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modRuntimeOnly")
            .apply {
                extendsFrom(project.configurations.getByName("runtimeOnly"))
                exclude(mapOf(
                    "group" to "net.fabricmc",
                    "module" to "fabric-loader"
                ))
            })

    val localRuntime: Configuration = project.configurations.maybeCreate("localRuntime").apply {
        extendsFrom(project.configurations.getByName("runtimeOnly"))
        exclude(mapOf(
            "group" to "net.fabricmc",
            "module" to "fabric-loader"
        ))
    }

    val modLocalRuntime: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modLocalRuntime")
            .apply {
                extendsFrom(project.configurations.getByName("localRuntime"))
                exclude(mapOf(
                    "group" to "net.fabricmc",
                    "module" to "fabric-loader"
                ))
            })

    val modImplementation: Configuration = registerConfiguration(
        project.configurations.maybeCreate("modImplementation")
            .apply {
                extendsFrom(project.configurations.getByName("implementation"))
                exclude(mapOf(
                    "group" to "net.fabricmc",
                    "module" to "fabric-loader"
                ))
            })

    val internalModRemapperConfiguration = project.configurations.maybeCreate("internalModRemapper").apply {
        exclude(mapOf(
            "group" to "net.fabricmc",
            "module" to "fabric-loader"
        ))
    }

    private val sourceSet: SourceSetContainer = project.extensions.getByType(SourceSetContainer::class.java)

    init {
        sourceSet.findByName("main")?.apply {
            compileClasspath += modCompileOnly + modImplementation
            runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
        }
        sourceSet.findByName("client")?.apply {
            compileClasspath += modCompileOnly + modImplementation
            runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
        }
        sourceSet.findByName("server")?.apply {
            compileClasspath += modCompileOnly + modImplementation
            runtimeClasspath += localRuntime + modRuntimeOnly + modLocalRuntime + modImplementation
        }

    }

    private fun registerConfiguration(configuration: Configuration): Configuration {
        configurations += configuration
        return configuration
    }

    fun remap() {
        configurations.forEach {
            preTransform(it)
        }
        tinyRemapper = TinyRemapper.newRemapper().withMappings(mcRemapper.getMappingProvider(mcRemapper.fallbackTarget, mcRemapper.fallbackTarget, mcRemapper.provider.targetNamespace.get()))
            .renameInvalidLocals(true)
            .inferNameFromSameLvIndex(true)
            .threads(Runtime.getRuntime().availableProcessors())
            .rebuildSourceFilenames(true)
//            .extension(MixinExtension())
            .build()
        val mc = mcRemapper.provider.getMinecraftCombinedWithMapping(mcRemapper.fallbackTarget)
        tinyRemapper.readClassPathAsync(mc)
        project.logger.warn("Remapping mods using ${mc}")
        tinyRemapper.readClassPathAsync(*mcRemapper.provider.mcLibraries.resolve().map { it.toPath() }.toTypedArray())
        configurations.forEach {
            transform(it)
        }
        configurations.forEach {
            postTransform(it)
        }
        tinyRemapper.finish()
    }

    private val dependencyMap = mutableMapOf<Configuration, MutableSet<Dependency>>()

    private fun preTransform(configuration: Configuration) {
        configuration.dependencies.forEach {
            internalModRemapperConfiguration.dependencies.add(it)
            dependencyMap.computeIfAbsent(configuration) { mutableSetOf() } += (it)
        }
        configuration.dependencies.clear()
    }

    private fun transform(configuration: Configuration) {
        dependencyMap[configuration]?.forEach {
            transformMod(it)
        }
    }

    private fun postTransform(configuration: Configuration) {
        dependencyMap[configuration]?.forEach {
            configuration.dependencies.add(
                project.dependencies.create(
                    project.files(getOutputs(it))
                )
            )
        }
    }

    private fun modTransformFolder(): Path {
        return mcRemapper.provider.parent.getLocalCache().resolve("modTransform").maybeCreate()

    }

    private lateinit var tinyRemapper: TinyRemapper
    private val outputMap = mutableMapOf<File, InputTag>()

    private fun transformMod(dependency: Dependency) {
        val files = internalModRemapperConfiguration.files(dependency)
        for (file in files) {
            if (file.extension == "jar") {
                val targetTag = tinyRemapper.createInputTag()
                tinyRemapper.readInputs(targetTag, file.toPath())
                outputMap[file] = targetTag
            }
        }
    }

    private fun getOutputs(dependency: Dependency): Set<File> {
        val mappingsDependecies = (mcRemapper.mappings.dependencies as Set<Dependency>).sortedBy { "${it.name}-${it.version}" }
        val combinedNames = mappingsDependecies.joinToString("+") { it.name + "-" + it.version }

        val outputs = mutableSetOf<File>()
        for (file in internalModRemapperConfiguration.files(dependency)) {
            if (file.extension == "jar") {
                val target = modTransformFolder()
                    .resolve("${file.nameWithoutExtension}-mapped-${combinedNames}-${mcRemapper.provider.targetNamespace.get()}.${file.extension}")
                if (target.exists()) {
                    outputs += target.toFile()
                    continue
                }
                OutputConsumerPath.Builder(target).build().use {
                    it.addNonClassFiles(file.toPath(), tinyRemapper, listOf(awRemapper, innerJarStripper) + NonClassCopyMode.FIX_META_INF.remappers)
                    tinyRemapper.apply(it, outputMap[file])
                }
            } else {
                outputs += file
            }
        }
        return outputs
    }

    private val awRemapper : ResourceRemapper = object : ResourceRemapper {
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
            val mappingTree = mcRemapper.mappingTree
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
                    var fallbackTo = mappingTree.getNamespaceId(mcRemapper.fallbackTarget)
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
                                    project.logger.warn("Method ${methodName}${methodDesc} not found in class ${className}")
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
                                    project.logger.warn("Field ${fieldName}${fieldDesc} not found in class ${className}")
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
}
