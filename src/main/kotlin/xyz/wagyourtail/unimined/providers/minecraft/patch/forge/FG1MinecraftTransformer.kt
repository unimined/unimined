package xyz.wagyourtail.unimined.providers.minecraft.patch.forge

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.consumerApply
import xyz.wagyourtail.unimined.deleteRecursively
import xyz.wagyourtail.unimined.maybeCreate
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.providers.minecraft.patch.jarmod.JarModMinecraftTransformer
import java.io.InputStream
import java.net.URI
import java.nio.file.*
import kotlin.io.path.*

class FG1MinecraftTransformer(project: Project, val parent: ForgeMinecraftTransformer) : JarModMinecraftTransformer(
    project,
    parent.provider,
    Constants.FORGE_PROVIDER
) {

    val afterForgeJarModder = JarModMinecraftTransformer(project, provider)
    var resolvedForgeDeps = false

    override fun afterEvaluate() {
        // get and add forge-src to mappings
        val forge = jarModConfiguration(EnvType.COMBINED).dependencies.last()
        if (forge.group != "net.minecraftforge" || !(forge.name == "minecraftforge" || forge.name == "forge")) {
            throw IllegalStateException("Invalid forge dependency found, if you are using multiple dependencies in the forge configuration, make sure the last one is the forge dependency!")
        }

        if (provider.minecraftDownloader.mcVersionCompare(provider.minecraftDownloader.version, "1.3") < 0) {
            jarModConfiguration(EnvType.COMBINED).dependencies.remove(forge)
        }

        val forgeSrc = "${forge.group}:${forge.name}:${forge.version}:src@zip"
        provider.parent.mappingsProvider.getMappings(EnvType.COMBINED).dependencies.apply {
           if (isEmpty())
               add(project.dependencies.create(forgeSrc))
        }

        super.afterEvaluate()
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        val atsOut = minecraft.let(consumerApply {
            // resolve forge
            val forge = jarModConfiguration(envType).resolve().firstOrNull()?.toPath() ?: jarModConfiguration(EnvType.COMBINED).resolve().firstOrNull()?.toPath() ?: throw IllegalStateException("No forge jar found for $envType!")


            val accessModder = AccessTransformerMinecraftTransformer(project, provider, envType).apply {
                atTransformers.add(::transformLegacyTransformer)
                atTransformers.add {
                    remapTransformer(
                        envType,
                        it,
                        "named", "searge", "official", "official"
                    )
                }

                // resolve ats from froge
                ZipReader.readInputStreamFor("fml_at.cfg", forge, false) {
                    addAccessTransformer(it)
                }
                ZipReader.readInputStreamFor("forge_at.cfg", forge, false) {
                    addAccessTransformer(it)
                }

                parent.accessTransformer?.let { addAccessTransformer(it) }
            }

            // resolve dyn libs
            ZipReader.readInputStreamFor("cpw/mods/fml/relauncher/CoreFMLLibraries.class", forge, false) {
                resolveDynLibs(getDynLibs(it))
            }

            // apply ats
            accessModder.transform(this)
        })

        // apply jarMod
        return afterForgeJarModder.transform(super.transform(atsOut))
    }

    private val forgeDeps: Configuration = project.configurations.maybeCreate(Constants.FORGE_DEPS)


    fun resolveDynLibs(wanted: Set<String>) {
        if (resolvedForgeDeps) return

        // provide and copy some old forge deps in
        val deps = listOf(
            Pair(
                Pair("argo-2.25.jar", "argo-2.25.jar"), project.dependencies.create(
                    "net.sourceforge.argo:argo:2.25"
                )
            ),
            Pair(
                Pair("guava-12.0.1.jar", "guava-12.0.1.jar"), project.dependencies.create(
                    "com.google.guava:guava:12.0.1"
                )
            ),
            Pair(
                Pair("guava-14.0-rc3.jar", "guava-14.0-rc3.jar"), project.dependencies.create(
                    "com.google.guava:guava:14.0-rc3"
                )
            ),
            Pair(
                Pair("asm-all-4.1.jar", "asm-all-4.1.jar"), project.dependencies.create(
                    "org.ow2.asm:asm-all:4.1"
                )
            ),
            Pair(
                Pair("bcprov-jdk15on-1.48.jar", "bcprov-jdk15on-148.jar"), project.dependencies.create(
                    "org.bouncycastle:bcprov-jdk15on:1.48"
                )
            ),
            Pair(
                Pair("bcprov-jdk15on-1.47.jar",  "bcprov-jdk15on-147.jar"), project.dependencies.create(
                    "org.bouncycastle:bcprov-jdk15on:1.47"
                )
            ),
        )
        deps.forEach { dep ->
            if (wanted.contains(dep.first.second)) {
                forgeDeps.dependencies.add(dep.second)
            }
        }

        // copy in
        val path = provider.clientWorkingDirectory.get().resolve("lib").toPath().maybeCreate()

        if (wanted.contains("asm-all-4.0.jar")) {
            FG1MinecraftTransformer::class.java.getResourceAsStream("/fmllibs/asm-all-4.0.jar").use { it1 ->
                val bytes = it1!!.readBytes()
                path.resolve("asm-all-4.0.jar")
                    .writeBytes(bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            }

            forgeDeps.dependencies.add(
                project.dependencies.create(
                    project.files(path.resolve("asm-all-4.0.jar").toString())
                )
            )
        }
        if (wanted.contains("scala-library.jar")) {
            FG1MinecraftTransformer::class.java.getResourceAsStream("/fmllibs/scala-library.jar").use { it1 ->
                val bytes = it1!!.readBytes()
                path.resolve("scala-library.jar")
                    .writeBytes(bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            }

            forgeDeps.dependencies.add(
                project.dependencies.create(
                    project.files(path.resolve("scala-library.jar").toString())
                )
            )
        }
        if (wanted.contains("argo-small-3.2.jar")) {
            FG1MinecraftTransformer::class.java.getResourceAsStream("/fmllibs/argo-small-3.2.jar").use { it1 ->
                val bytes = it1!!.readBytes()
                path.resolve("argo-small-3.2.jar")
                    .writeBytes(bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            }

            forgeDeps.dependencies.add(
                project.dependencies.create(
                    project.files(path.resolve("argo-small-3.2.jar").toString())
                )
            )
        }
        if (wanted.contains("deobfuscation_data_1.5.2.zip")) {
            FG1MinecraftTransformer::class.java.getResourceAsStream("/fmllibs/deobfuscation_data_1.5.2.zip")
                .use { it1 ->
                    val bytes = it1!!.readBytes()
                    path.resolve("deobfuscation_data_1.5.2.zip")
                        .writeBytes(bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                }

            forgeDeps.dependencies.add(
                project.dependencies.create(
                    project.files(path.resolve("deobfuscation_data_1.5.2.zip").toString())
                )
            )
        }

        resolvedForgeDeps = true
        forgeDeps.resolve()

        for (dep in deps) {
            if (wanted.contains(dep.first.second)) {
                forgeDeps.files(dep.second).forEach { file ->
                    if (file.name == dep.first.first) {
                        file.copyTo(path.resolve(dep.first.second).toFile(), true)
                    } else {
                        file.copyTo(path.resolve(file.name).toFile(), true)
                    }
                }
            }
        }
    }

    override fun sourceSets(sourceSets: SourceSetContainer) {
        val main = sourceSets.getByName("main")

        main.compileClasspath += forgeDeps
        main.runtimeClasspath += forgeDeps

        if (provider.minecraftDownloader.client) {
            sourceSets.findByName("client")?.let {
                it.compileClasspath += forgeDeps
                it.runtimeClasspath += forgeDeps
            }
        }

        if (provider.minecraftDownloader.server) {
            sourceSets.findByName("server")?.let {
                it.compileClasspath += forgeDeps
                it.runtimeClasspath += forgeDeps
            }
        }
    }

    override fun applyClientRunConfig(tasks: TaskContainer) {
        provider.provideRunClientTask(tasks) {
            it.jvmArgs.add("-Dminecraft.applet.TargetDirectory=\"${it.workingDir.absolutePath}\"")
        }
    }

    override fun afterRemap(envType: EnvType, namespace: String, baseMinecraft: Path): Path {
        if (namespace == "named") {
            val target = baseMinecraft.parent.resolve("${baseMinecraft.nameWithoutExtension}-stripped.${baseMinecraft.extension}")

            if (target.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                return target
            }

            Files.copy(baseMinecraft, target, StandardCopyOption.REPLACE_EXISTING)
            val mc = URI.create("jar:${target.toUri()}")
            try {
                FileSystems.newFileSystem(mc, mapOf("mutable" to true), null).use { out ->
                    val dynPath = out.getPath("cpw/mods/fml/relauncher/CoreFMLLibraries.class")

                    val dyn = if (dynPath.exists()) dynPath.inputStream().use { it ->
                        getDynLibs(it)
                    } else setOf()

                    if (dyn.any { it.contains("argo") }) {
                        out.getPath("argo").apply {
                            if (exists()) {
                                deleteRecursively()
                            }
                        }
                    }
                    if (dyn.any { it.contains("bcprov") }) {
                        out.getPath("org/bouncycastle").apply {
                            if (exists()) {
                                deleteRecursively()
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                target.deleteExisting()
                throw e
            }
            return target
        }
        return baseMinecraft
    }

    private fun getDynLibs(stream: InputStream): Set<String> {
        project.logger.warn("Getting dynamic libraries from FML")
        val classReader = ClassReader(stream)

        val classNode = ClassNode()
        classReader.accept(classNode, 0)

        // get string array stored to libraries from clinit
        val clInit = classNode.methods.first { it.name == "<clinit>" }
        val iterator = clInit.instructions.iterator()
        val outSet = mutableSetOf<String>()
        var arrayFlag = false
        while (iterator.hasNext()) {
            val insn = iterator.next()
            if (insn is TypeInsnNode && insn.opcode == Opcodes.ANEWARRAY && insn.desc == "java/lang/String") {
                arrayFlag = true
            }
            if (arrayFlag) {
                if (insn is LdcInsnNode) {
                    outSet.add(insn.cst as String)
                }
                if (insn is MethodInsnNode && insn.opcode == Opcodes.INVOKESTATIC && insn.name == "debfuscationDataName") {
                    outSet.add("deobfuscation_data_${provider.minecraftDownloader.version}.zip")
                }
            }
            if (insn is FieldInsnNode && insn.opcode == Opcodes.PUTSTATIC) {
                if (insn.name == "libraries") {
                    break
                } else {
                    arrayFlag = false
                    outSet.clear()
                }
            }
        }
        return outSet
    }
}

