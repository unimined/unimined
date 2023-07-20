package xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod

import net.lenni0451.classtransform.TransformerManager
import net.lenni0451.classtransform.utils.tree.IClassProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency
import xyz.wagyourtail.unimined.api.minecraft.patch.JarModAgentPatcher
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.mods.task.RemapJarTaskImpl
import xyz.wagyourtail.unimined.util.getTempFilePath
import xyz.wagyourtail.unimined.util.openZipFileSystem
import xyz.wagyourtail.unimined.util.withSourceSet
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


open class JarModAgentMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider,
    jarModProvider: String = "jarMod",
    providerName: String = "JarModAgent"
) : JarModMinecraftTransformer(
    project, provider, jarModProvider, providerName
), JarModAgentPatcher {
    companion object {
        private const val JMA_TRANSFORMERS = "jma.transformers"
        private const val JMA_PRIORITY_CLASSPATH = "jma.priorityClasspath"
    }

    @Deprecated("may violate mojang's EULA... use at your own risk. this is not recommended and is only here for legacy reasons and testing.")
    override var compiletimeTransforms: Boolean = false

    override var jarModAgent = project.configurations.maybeCreate("jarModAgent".withSourceSet(provider.sourceSet)).also {
        provider.minecraft.extendsFrom(it)
    }

    val jmaFile by lazy {
        jarModAgent.resolve().first { it.extension == "jar" }.toPath()
    }

    private val transforms = mutableListOf<String>()

    override fun transforms(transform: String) {
        this.transforms.add(transform)
    }

    override fun transforms(transforms: List<String>) {
        this.transforms.addAll(transforms)
    }

    override fun apply() {
        if (jarModAgent.dependencies.isEmpty()) {
            project.unimined.wagYourMaven("snapshots")
            jarModAgent.dependencies.add(
                project.dependencies.create(
                    "xyz.wagyourtail.unimined:jarmod-agent:0.1.2-SNAPSHOT:all"
                ).also {
                    (it as ExternalDependency).isTransitive = false
                }
            )
        }

        super.apply()
    }

    override fun applyClientRunTransform(config: RunConfig) {
        super.applyClientRunTransform(config)
        applyJarModAgent(config)
    }

    override fun applyServerRunTransform(config: RunConfig) {
        super.applyServerRunTransform(config)
        applyJarModAgent(config)
    }

    private fun applyJarModAgent(config: RunConfig) {
        if (transforms.isNotEmpty()) {
            config.jvmArgs.add("-D${JMA_TRANSFORMERS}=${transforms.joinToString(File.pathSeparator)}")
        }
        // priority classpath
        val priorityClasspath = detectProjectSourceSets().map { it.output.classesDirs.toMutableSet().also {set-> it.output.resourcesDir.let { set.add(it) } } }.flatten()
        if (priorityClasspath.isNotEmpty()) {
            config.jvmArgs.add("-D${JMA_PRIORITY_CLASSPATH}=${priorityClasspath.joinToString(File.pathSeparator) { it.absolutePath }}")
        }
        config.jvmArgs.add("-javaagent:${jmaFile}")
        //TODO: add mods to priority classpath, and resolve their jma.transformers
    }

    override fun afterRemapJarTask(remapJarTask: RemapJarTask, output: Path) {
        super.afterRemapJarTask(remapJarTask, output)
        @Suppress("DEPRECATION")
        if (compiletimeTransforms && transforms.isNotEmpty()) {
            project.logger.lifecycle("[Unimined/JarModAgentTransformer] Running compile time transforms for ${remapJarTask.name}...")

            val classpath = (remapJarTask as RemapJarTaskImpl).provider.sourceSet.runtimeClasspath.files.toMutableSet()

            // remove minecraft
            classpath.removeIf { provider.isMinecraftJar(it.toPath()) }
            // add back with correct mappings
            val targetNamespace = remapJarTask.prodNamespace ?: this.prodNamespace
            classpath.add(provider.getMinecraft(targetNamespace, targetNamespace).toFile())
            // add input jar
            // copy output to temp
            // add temp to classpath
            val temp = getTempFilePath(output.nameWithoutExtension, "jar")
            Files.copy(output, temp)
            classpath.add(temp.toFile())

            //TODO: remove and add back mods with correct mappings
            val classLoader = URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray())

            project.logger.lifecycle("[Unimined/JarModAgentTransformer] Loading transforms for $output from $transforms")
            project.logger.info("[Unimined/JarModAgentTransformer] Classpath: $classpath")

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

            for (transforms in this.transforms) {
                classLoader.getResourceAsStream(transforms).use { `is` ->
                    if (`is` == null) {
                        throw IOException("Could not find transform file: $transforms")
                    }
                    project.logger.info("[Unimined/JarModAgentTransformer] Loading transforms: $transforms")
                    BufferedReader(InputStreamReader(`is`)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            transformers.add(line!!)
                        }
                    }
                }
            }

            var fail = 0
            for (tf in transformers) {
                try {
                    transformer.addTransformer(tf)
                } catch (e: Exception) {
                    project.logger.error("[Unimined/JarModAgentTransformer] Failed to load transform: $transformer")
                    e.printStackTrace()
                    fail++
                }
            }
            if (fail > 0) {
                throw RuntimeException("Failed to load $fail transforms")
            }

            // transform
            project.logger.lifecycle("[Unimined/JarModAgentTransformer] Transforming...")
            output.openZipFileSystem(mapOf("mutable" to true)).use { out ->
                val fd = TransformerManager::class.java.getDeclaredField("transformedClasses")
                fd.isAccessible = true
                @Suppress("UNCHECKED_CAST") val transformedClasses = fd.get(transformer) as Set<String>
                for (name in transformedClasses) {
                    val bytes = classLoader.getResourceAsStream(name.replace('.', '/') + ".class")?.readBytes()
                    if (bytes == null) {
                        project.logger.warn("[Unimined/JarModAgentTransformer] Failed to find class $name")
                        continue
                    }
                    project.logger.info("[Unimined/JarModAgentTransformer] Transforming $name")
                    val path = out.getPath(name.replace('.', '/') + ".class")
                    path.parent?.createDirectories()
                    path.writeBytes(transformer.transform(name, bytes))
                }

                // shade net.lenni0451.classtransform.InjectionCallback
                val injectionCallback = JarModAgentMinecraftTransformer::class.java.classLoader.getResourceAsStream("net/lenni0451/classtransform/InjectionCallback.class")!!.readBytes()
                val path = out.getPath("net/lenni0451/classtransform/InjectionCallback.class")
                path.parent?.createDirectories()
                path.writeBytes(injectionCallback)
            }
        }
    }

}