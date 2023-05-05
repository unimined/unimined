package xyz.wagyourtail.unimined.minecraft.patch.jarmod

import net.fabricmc.mappingio.format.ZipReader
import net.lenni0451.classtransform.TransformerManager
import net.lenni0451.classtransform.utils.tree.IClassProvider
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import xyz.wagyourtail.unimined.api.Constants
import xyz.wagyourtail.unimined.api.launch.LaunchConfig
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.JarModAgentPatcher
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.minecraft.MinecraftProviderImpl
import xyz.wagyourtail.unimined.util.getTempFilePath
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.io.path.createDirectories
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeBytes


class JarModAgentMinecraftTransformer(
    project: Project,
    provider: MinecraftProviderImpl,
    private val jarModProvider: String = Constants.JARMOD_PROVIDER,
) : JarModMinecraftTransformer(
    project, provider, Constants.JARMOD_PROVIDER, "JarModAgent"
), JarModAgentPatcher {
    companion object {
        private val JMA_TRANSFORMERS = "jma.transformers"
        private val JMA_PRIORITY_CLASSPATH = "jma.priorityClasspath"
    }

    override var transforms: String? = null

    override var compiletimeTransforms: Boolean = false

    override var jarModAgent = project.configurations.maybeCreate(Constants.JARMOD_AGENT_PROVIDER)

    val jmaFile by lazy {
        jarModAgent.resolve().first { it.extension == "jar" }.toPath()
    }

    override fun afterEvaluate() {
        if (jarModAgent.dependencies.isEmpty()) {
            project.repositories.maven {
                it.url = project.uri("https://maven.wagyourtail.xyz/snapshots/")
                it.name = "WagYourTail's Maven Snapshots"
            }
            jarModAgent.dependencies.add(
                project.dependencies.create(
                    "xyz.wagyourtail.unimined:jarmod-agent:0.1.0-SNAPSHOT"
                )
            )
        }

        super.afterEvaluate()
    }

    override fun sourceSets(sourceSets: SourceSetContainer) {
        for (sourceSet in provider.combinedSourceSets) {
            sourceSet.compileClasspath += jarModAgent
            sourceSet.runtimeClasspath += jarModAgent
        }

        for (sourceSet in provider.clientSourceSets) {
            sourceSet.compileClasspath += jarModAgent
            sourceSet.runtimeClasspath += jarModAgent
        }

        for (sourceSet in provider.serverSourceSets) {
            sourceSet.compileClasspath += jarModAgent
            sourceSet.runtimeClasspath += jarModAgent
        }
    }

    override fun applyClientRunTransform(config: LaunchConfig) {
        super.applyClientRunTransform(config)
        applyJarModAgent(config)
    }

    override fun applyServerRunTransform(config: LaunchConfig) {
        super.applyServerRunTransform(config)
        applyJarModAgent(config)
    }

    private fun applyJarModAgent(config: LaunchConfig) {
        transforms?.let {
               config.jvmArgs.add("-D${JMA_TRANSFORMERS}=$it")
        }
        // priority classpath
        val priorityClasspath = detectProjectSourceSets().map { it.output.classesDirs.toMutableSet().also {set-> it.output.resourcesDir.let { set.add(it) } } }.flatten()
        if (priorityClasspath.isNotEmpty()) {
            config.jvmArgs.add("-D${JMA_PRIORITY_CLASSPATH}=${priorityClasspath.joinToString(File.pathSeparator) { it.absolutePath }}")
        }
        config.jvmArgs.add("-javaagent:${jmaFile}")
        //TODO: add mods to priority classpath, and resolve their jma.transformers
    }

    @Suppress("DEPRECATION")
    override fun afterRemapJarTask(remapJarTask: RemapJarTask, output: Path) {
        super.afterRemapJarTask(remapJarTask, output)
        if (compiletimeTransforms && transforms != null) {
            project.logger.lifecycle("Running compile time transforms for ${remapJarTask.name}...")

            val envType = remapJarTask.envType.getOrElse(project.minecraft.defaultEnv)!!
            val mappings = remapJarTask.targetNamespace.getOrElse(this.prodNamespace)!!

            val classpath = remapJarTask.sourceSet.runtimeClasspath.files.toMutableSet()
            // remove minecraft
            classpath.removeIf { project.configurations.getByName("minecraft").contains(it) }
            // add back with correct mappings
            val mc = project.minecraft.getMinecraftWithMapping(envType, mappings, mappings)
            classpath.add(mc.toFile())
            // add input jar
            // copy output to temp
            // add temp to classpath
            val temp = getTempFilePath(output.nameWithoutExtension, "jar")
            Files.copy(output, temp)
            classpath.add(temp.toFile())
            //TODO: remove and add back mods with correct mappings
            val classLoader = URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray())

            project.logger.lifecycle("Loading transforms for $output from $transforms")
            project.logger.info("Classpath: $classpath")

            val transformer = TransformerManager(object : IClassProvider {
                override fun getClass(name: String): ByteArray? {
                    return classLoader.getResourceAsStream(name.replace('.', '/') + ".class")?.readBytes()
                }

                override fun getAllClasses(): MutableMap<String, Supplier<ByteArray>> {
                    throw UnsupportedOperationException()
                }
            })

            // add transformers
            val transformers: MutableList<String> = ArrayList()
            classLoader.getResourceAsStream(transforms).use { `is` ->
                if (`is` == null) {
                    throw IOException("Could not find transform file: $transforms")
                }
                project.logger.info("Loading transforms: $transforms")
                BufferedReader(InputStreamReader(`is`)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        transformers.add(line!!)
                    }
                }
            }
            var fail = 0
            for (tf in transformers) {
                try {
                    transformer.addTransformer(tf)
                } catch (e: Exception) {
                    project.logger.error("Failed to load transform: $transformer")
                    e.printStackTrace()
                    fail++
                }
            }
            if (fail > 0) {
                throw RuntimeException("Failed to load $fail transforms")
            }

            // transform
            project.logger.lifecycle("Transforming...")
            ZipReader.openZipFileSystem(output, mapOf("mutable" to true)).use { out ->
                val fd = TransformerManager::class.java.getDeclaredField("transformedClasses")
                fd.isAccessible = true
                val transformedClasses = fd.get(transformer) as Set<String>
                for (name in transformedClasses) {
                    val bytes = classLoader.getResourceAsStream(name.replace('.', '/') + ".class")?.readBytes()
                    if (bytes == null) {
                        project.logger.warn("Failed to find class $name")
                        continue
                    }
                    project.logger.info("Transforming $name")
                    val path = out.getPath(name.replace('.', '/') + ".class");
                    path.parent?.createDirectories()
                    path.writeBytes(transformer.transform(name, bytes))
                }

                // shade net.lenni0451.classtransform.InjectionCallback
                val injectionCallback = JarModAgentMinecraftTransformer::class.java.classLoader.getResourceAsStream("net/lenni0451/classtransform/InjectionCallback.class")!!.readBytes()
                val path = out.getPath("net/lenni0451/classtransform/InjectionCallback.class");
                path.parent?.createDirectories()
                path.writeBytes(injectionCallback)
            }
        }
    }

}