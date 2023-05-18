package xyz.wagyourtail.unimined.internal.minecraft.patch.forge.fg1

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.ForgeMinecraftTransformer
import xyz.wagyourtail.unimined.minecraft.transform.merge.ClassMerger
import xyz.wagyourtail.unimined.util.deleteRecursively
import xyz.wagyourtail.unimined.util.withSourceSet
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.file.*
import kotlin.io.path.*

class FG1MinecraftTransformer(project: Project, val parent: ForgeMinecraftTransformer): JarModMinecraftTransformer(
    project,
    parent.provider,
    "forge",
    "FG1"
) {

    override val prodNamespace: MappingNamespace
        get() = MappingNamespace.OFFICIAL

    var resolvedForgeDeps = false

    override val merger: ClassMerger
        get() = parent.merger

    override fun apply() {
        // get and add forge-src to mappings
        val forge = jarModConfiguration(EnvType.COMBINED).dependencies.last()
        if (forge.group != "net.minecraftforge" || !(forge.name == "minecraftforge" || forge.name == "forge")) {
            throw IllegalStateException("Invalid forge dependency found, if you are using multiple dependencies in the forge configuration, make sure the last one is the forge dependency!")
        }

        if (provider.minecraftData.mcVersionCompare(provider.version, "1.3") < 0) {
            jarModConfiguration(EnvType.COMBINED).dependencies.remove(forge)
        }

        provider.mappings.mappingsDeps.apply {
            if (isEmpty())
                provider.mappings.mapping("${forge.group}:${forge.name}:${forge.version}:src@zip")
        }

        super.apply()
    }

    private val forgeDeps: Configuration = project.configurations.maybeCreate("forgeDeps".withSourceSet(provider.sourceSet)).apply {
        extendsFrom(project.configurations.getByName("implementation".withSourceSet(provider.sourceSet)))
    }


    fun resolveDynLibs(workingDirectory: File, wanted: Set<String>) {
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
                Pair("bcprov-jdk15on-1.47.jar", "bcprov-jdk15on-147.jar"), project.dependencies.create(
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
        val path = workingDirectory.resolve("lib").toPath().createDirectories()

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

    override fun applyClientRunTransform(config: RunConfig) {
        super.applyClientRunTransform(config)

        val forge = jarModConfiguration(EnvType.CLIENT).resolve().firstOrNull()?.toPath()
            ?: jarModConfiguration(EnvType.COMBINED).resolve().firstOrNull()?.toPath()
            ?: throw IllegalStateException("No forge jar found for ${EnvType.CLIENT}!")


        // resolve dyn libs
        ZipReader.readInputStreamFor("cpw/mods/fml/relauncher/CoreFMLLibraries.class", forge, false) {
            resolveDynLibs(config.workingDir, getDynLibs(it))
        }

        config.jvmArgs.add("-Dminecraft.applet.TargetDirectory=\"${config.workingDir.absolutePath}\"")
        if (parent.mainClass != null) config.mainClass = parent.mainClass!!
    }

    override fun afterRemap(baseMinecraft: MinecraftJar): MinecraftJar {
        val out = fixForge(baseMinecraft)
        return ZipReader.openZipFileSystem(out.path).use { fs ->
            parent.applyATs(
                out,
                listOf(fs.getPath("forge_at.cfg"), fs.getPath("fml_at.cfg")).filter { Files.exists(it) })
        }
    }

    private fun fixForge(baseMinecraft: MinecraftJar): MinecraftJar {
        if (baseMinecraft.mappingNamespace.type == MappingNamespace.Type.NAMED) {
            val target = MinecraftJar(
                baseMinecraft,
                patches = baseMinecraft.patches + "fixForge",
            )

            if (target.path.exists() && !project.gradle.startParameter.isRefreshDependencies) {
                return target
            }

            Files.copy(baseMinecraft.path, target.path, StandardCopyOption.REPLACE_EXISTING)
            val mc = URI.create("jar:${target.path.toUri()}")
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
                target.path.deleteIfExists()
                throw e
            }
            return target
        }
        return baseMinecraft
    }

    private fun getDynLibs(stream: InputStream): Set<String> {
        project.logger.lifecycle("Getting dynamic libraries from FML")
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
                    outSet.add("deobfuscation_data_${provider.version}.zip")
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