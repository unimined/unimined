package xyz.wagyourtail.unimined.providers.minecraft.patch.forge

import net.fabricmc.mappingio.format.ZipReader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import xyz.wagyourtail.unimined.Constants
import xyz.wagyourtail.unimined.OSUtils
import xyz.wagyourtail.unimined.deleteRecursively
import xyz.wagyourtail.unimined.maybeCreate
import xyz.wagyourtail.unimined.providers.minecraft.EnvType
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.providers.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.providers.minecraft.patch.jarmod.JarModMinecraftTransformer
import java.io.File
import java.net.URI
import java.nio.file.*
import kotlin.io.path.*

class FG1MinecraftTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(
    project,
    provider
) {

    val forgeJarModder = JarModMinecraftTransformer(project, provider, Constants.FORGE_PROVIDER)
    val afterForgeJarModder = JarModMinecraftTransformer(project, provider)
    val accessTransformer: File? = null

    override fun afterEvaluate() {
        // get and add forge-src to mappings
        val forge = forgeJarModder.jarModConfiguration(EnvType.COMBINED).dependencies.last()

        val forgeSrc = "${forge.group}:${forge.name}:${forge.version}:src@zip"
        provider.parent.mappingsProvider.getMappings(EnvType.COMBINED).dependencies.apply {
           if (isEmpty())
               add(project.dependencies.create(forgeSrc))
        }

        // replace forge with universal
        val forgeUniversal = "${forge.group}:${forge.name}:${forge.version}:universal@zip"
        forgeJarModder.jarModConfiguration(EnvType.COMBINED).dependencies.apply {
            remove(forge)
            if (!provider.disableCombined.get()) {
                add(project.dependencies.create(forgeUniversal))
            }
        }

        // add forge client/server if universal is disabled
        val forgeClient = "${forge.group}:${forge.name}:${forge.version}:client@zip"
        val forgeServer = "${forge.group}:${forge.name}:${forge.version}:server@zip"
        if (provider.disableCombined.get()) {
            forgeJarModder.jarModConfiguration(EnvType.CLIENT).dependencies.apply {
                clear()
                add(project.dependencies.create(forgeClient))
            }
            forgeJarModder.jarModConfiguration(EnvType.SERVER).dependencies.apply {
                clear()
                add(project.dependencies.create(forgeServer))
            }
        }

        super.afterEvaluate()
    }

    override fun transform(envType: EnvType, baseMinecraft: Path): Path {
        val accessModder = AccessTransformerMinecraftTransformer(project, provider)
        if (accessTransformer != null) {
            accessModder.addAccessTransformer(accessTransformer)
        }

        // resolve forge
        val forge = forgeJarModder.jarModConfiguration(envType).resolve().firstOrNull()?.toPath()
            ?: forgeJarModder.jarModConfiguration(EnvType.COMBINED).resolve().first().toPath()
        // resolve ats from froge
        ZipReader.readInputStreamFor("fml_at.cfg", forge, false) {
            accessModder.addAccessTransformer(it)
        }
        ZipReader.readInputStreamFor("forge_at.cfg", forge, false) {
            accessModder.addAccessTransformer(it)
        }
        // apply ats
        val atsOut = accessModder.transform(envType, baseMinecraft)
        // apply jarMod
        return afterForgeJarModder.transform(envType, forgeJarModder.transform(envType, atsOut))
    }

    private val forgeDeps: Configuration = project.configurations.maybeCreate(Constants.FORGE_DEPS)


    override fun sourceSets(sourceSets: SourceSetContainer) {
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
            )
        )
        deps.forEach { dep ->
            forgeDeps.dependencies.add(dep.second)
        }

        // copy in
        val path = provider.clientWorkingDirectory.get().resolve("lib").toPath().maybeCreate()

        //TODO: don't rely on this random path, (maven has different hash that's why we're doing this)
        URI.create("https://ftp.osuosl.org/pub/netbeans/binaries/98308890597ACB64047F7E896638E0D98753AE82-asm-all-4.0.jar").toURL().openStream().use { it1 ->
            val bytes = it1.readBytes()
            path.resolve("asm-all-4.0.jar")
                .writeBytes(bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
        URI.create("https://web.archive.org/web/20130708223654if_/http://files.minecraftforge.net/fmllibs/scala-library.jar").toURL().openStream().use { it1 ->
            val bytes = it1.readBytes()
            path.resolve("scala-library.jar")
                .writeBytes(bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
        URI.create("https://web.archive.org/web/20130708175450if_/http://files.minecraftforge.net/fmllibs/argo-small-3.2.jar").toURL().openStream().use { it1 ->
            val bytes = it1.readBytes()
            path.resolve("argo-small-3.2.jar")
                .writeBytes(bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
        URI.create("https://web.archive.org/web/20140626042316if_/http://files.minecraftforge.net/fmllibs/deobfuscation_data_1.5.2.zip").toURL().openStream().use { it1 ->
            val bytes = it1.readBytes()
            path.resolve("deobfuscation_data_1.5.2.zip")
                .writeBytes(bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }

        forgeDeps.dependencies.add(
            project.dependencies.create(
                project.files(path.resolve("asm-all-4.0.jar").toString())
            )
        )

        forgeDeps.dependencies.add(
            project.dependencies.create(
                project.files(path.resolve("scala-library.jar").toString())
            )
        )

        forgeDeps.dependencies.add(
            project.dependencies.create(
                project.files(path.resolve("argo-small-3.2.jar").toString())
            )
        )

        forgeDeps.dependencies.add(
            project.dependencies.create(
                project.files(path.resolve("deobfuscation_data_1.5.2.zip").toString())
            )
        )

        forgeDeps.resolve()

        for (dep in deps) {
            forgeDeps.files(dep.second).forEach { file ->
                if (file.name == dep.first.first) {
                    file.copyTo(path.resolve(dep.first.second).toFile(), true)
                } else {
                    file.copyTo(path.resolve(file.name).toFile(), true)
                }
            }
        }

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
                    out.getPath("argo").apply {
                        if (exists()) {
                            deleteRecursively()
                        }
                    }
                    out.getPath("org/bouncycastle").apply {
                        if (exists()) {
                            deleteRecursively()
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
}

