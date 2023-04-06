package xyz.wagyourtail.unimined.minecraft.patch.jarmod

import net.fabricmc.mappingio.format.ZipReader
import net.lenni0451.classtransform.TransformerManager
import net.lenni0451.classtransform.utils.tree.IClassProvider
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.minecraft.minecraft
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.JarModPatcher
import xyz.wagyourtail.unimined.api.tasks.JarModTransformResolveTask
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLClassLoader
import java.util.function.Supplier
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

abstract class JarModTransformResolveTaskImpl : JarModTransformResolveTask() {

    @TaskAction
    fun run() {
        val input = inputFile.get().asFile
        val output = archiveFile.get().asFile

        val transf = if (!transforms.isPresent) {
            val patcher = project.minecraft.mcPatcher as JarModPatcher
            patcher.transforms
        } else {
            transforms.get()
        }

        if (transf == null) {
            input.copyTo(output, overwrite = true)
            return
        }

        val envType = remapJarTask.envType.getOrElse(project.minecraft.defaultEnv)!!
        val mappings = remapJarTask.targetNamespace.getOrElse(project.minecraft.mcPatcher.prodNamespace)!!

        val classpath = sourceSet.runtimeClasspath.files.toMutableSet()
        // remove minecraft
        classpath.removeIf { project.configurations.getByName("minecraft").contains(it) }
        // add back with correct mappings
        val mc = project.minecraft.getMinecraftWithMapping(envType, mappings, mappings)
        classpath.add(mc.toFile())
        // add input jar
        classpath.add(input)
        //TODO: remove and add back mods with correct mappings
        val classLoader = URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray())

        project.logger.lifecycle("Loading transforms for $input from $transf")
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
        classLoader.getResourceAsStream(transf).use { `is` ->
            if (`is` == null) {
                throw IOException("Could not find transform file: $transf")
            }
            project.logger.info("Loading transforms: $transf")
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

        ZipReader.openZipFileSystem(output.toPath(), mapOf("create" to true)).use { out ->
            ZipReader.forEachInZip(input.toPath()) { path, stream ->
                val path = out.getPath(path)
                path.parent?.createDirectories()
                path.writeBytes(stream.readBytes())
            }
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
            val injectionCallback = JarModTransformResolveTaskImpl::class.java.classLoader.getResourceAsStream("net/lenni0451/classtransform/InjectionCallback.class")!!.readBytes()
            val path = out.getPath("net/lenni0451/classtransform/InjectionCallback.class");
            path.parent?.createDirectories()
            path.writeBytes(injectionCallback)
        }


    }


}
