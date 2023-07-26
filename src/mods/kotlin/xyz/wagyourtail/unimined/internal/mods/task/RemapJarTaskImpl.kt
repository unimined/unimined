package xyz.wagyourtail.unimined.internal.mods.task

import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.patch.ForgeLikePatcher
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.aw.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.BetterMixinExtension
import xyz.wagyourtail.unimined.util.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.Manifest
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.outputStream

abstract class RemapJarTaskImpl @Inject constructor(@get:Internal val provider: MinecraftConfig): RemapJarTask() {

    override fun devNamespace(namespace: String) {
        val delegate: FinalizeOnRead<MappingNamespaceTree.Namespace> = RemapJarTask::class.getField("devNamespace")!!.getDelegate(this) as FinalizeOnRead<MappingNamespaceTree.Namespace>
        delegate.setValueIntl(LazyMutable { provider.mappings.getNamespace(namespace) })
    }

    override fun devFallbackNamespace(namespace: String) {
        val delegate: FinalizeOnRead<MappingNamespaceTree.Namespace> = RemapJarTask::class.getField("devFallbackNamespace")!!.getDelegate(this) as FinalizeOnRead<MappingNamespaceTree.Namespace>
        delegate.setValueIntl(LazyMutable { provider.mappings.getNamespace(namespace) })
    }

    override fun prodNamespace(namespace: String) {
        val delegate: FinalizeOnRead<MappingNamespaceTree.Namespace> = RemapJarTask::class.getField("prodNamespace")!!.getDelegate(this) as FinalizeOnRead<MappingNamespaceTree.Namespace>
        delegate.setValueIntl(LazyMutable { provider.mappings.getNamespace(namespace) })
    }

    @TaskAction
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    fun run() {
        val prodNs = prodNamespace ?: provider.mcPatcher.prodNamespace!!
        val devNs = devNamespace ?: provider.mappings.devNamespace!!
        val devFNs = devFallbackNamespace ?: provider.mappings.devFallbackNamespace!!

        val path = provider.mappings.getRemapPath(
            devNs,
            devFNs,
            prodNs,
            prodNs
        )

        if (path.isEmpty()) {
            // copy into output
            from(inputFile)
            copy()
            return
        }

        val last = path.last()
        project.logger.lifecycle("[Unimined/RemapJar ${this.path}] remapping output ${inputFile.get().asFile.name} from $devNs/$devFNs to $prodNs")
        project.logger.info("[Unimined/RemapJar]    $devNs -> ${path.joinToString(" -> ") { it.name }}")
        var prevTarget = inputFile.get().asFile.toPath()
        var prevNamespace = devNs
        var prevPrevNamespace = devFNs
        for (i in path.indices) {
            val step = path[i]
            project.logger.info("[Unimined/RemapJar]    $step")
            val nextTarget = project.buildDir.resolve("tmp").resolve(name).toPath().resolve("${inputFile.get().asFile.nameWithoutExtension}-temp-${step.name}.jar")
            nextTarget.deleteIfExists()
            val mcNamespace = prevNamespace
            val mcFallbackNamespace = prevPrevNamespace

            val mc = provider.getMinecraft(
                mcNamespace,
                mcFallbackNamespace
            )
            remapToInternal(prevTarget, nextTarget, prevNamespace, step, mc)
            prevTarget = nextTarget
            prevPrevNamespace = prevNamespace
            prevNamespace = step
        }
        provider.mcPatcher.afterRemapJarTask(this, prevTarget)
        from(project.zipTree(prevTarget))

        // merge in manifest from input jar
        inputFile.get().asFile.toPath().readZipInputStreamFor("META-INF/MANIFEST.MF", false) { inp ->
            // write to temp file
            val inpTmp = project.buildDir.resolve("tmp").resolve(name).toPath().resolve("input-manifest.MF")
            inpTmp.outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
                inp.copyTo(out)
            }
            this.manifest {
                it.from(inpTmp)
            }
        }

        copy()
    }

    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    protected fun remapToInternal(
        from: Path,
        target: Path,
        fromNs: MappingNamespaceTree.Namespace,
        toNs: MappingNamespaceTree.Namespace,
        mc: Path
    ) {
        val remapperB = TinyRemapper.newRemapper()
            .withMappings(
                provider.mappings.getTRMappings(
                    fromNs to toNs,
                    false
                )
            )
            .skipLocalVariableMapping(true)
            .ignoreConflicts(true)
            .threads(Runtime.getRuntime().availableProcessors())
        val classpath = KotlinClasspathService.getOrCreateIfRequired(project)
        if (classpath != null) {
            remapperB.extension(KotlinRemapperClassloader.create(classpath).tinyRemapperExtension)
        }
        val betterMixinExtension = BetterMixinExtension(project.gradle.startParameter.logLevel)
        remapperB.extension(betterMixinExtension)
        provider.minecraftRemapper.tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        remapper.readClassPathAsync(
            *provider.sourceSet.runtimeClasspath.files.map { it.toPath() }
                .filter { !provider.isMinecraftJar(it) }
                .filter { it.exists() }
                .toTypedArray()
        )
        remapper.readClassPathAsync(mc)
        betterMixinExtension.preRead(from, "${project.rootProject.name}.refmap.json")
        remapper.readInputsAsync(from)
        target.parent.createDirectories()
        try {
            OutputConsumerPath.Builder(target).build().use {
                it.addNonClassFiles(
                    from,
                    remapper,
                    listOf(
                        AccessWidenerMinecraftTransformer.AwRemapper(
                            if (fromNs.named) "named" else fromNs.name,
                            if (toNs.named) "named" else toNs.name
                        ),
                        AccessTransformerMinecraftTransformer.AtRemapper(project.logger, remapATToLegacy.getOrElse((provider.mcPatcher as? ForgeLikePatcher)?.remapAtToLegacy == true)!!),
                        betterMixinExtension.resourceRemapper()
                    )
                )
                remapper.apply(it)
            }
        } catch (e: Exception) {
            target.deleteIfExists()
            throw e
        }
        remapper.finish()

        target.openZipFileSystem(mapOf("mutable" to true)).use {
            betterMixinExtension.write(it)
        }
    }

}