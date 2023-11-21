package xyz.wagyourtail.unimined.internal.mods.task

import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.mixin.MixinRemapOptions
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.patch.ForgeLikePatcher
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.aw.AccessWidenerMinecraftTransformer
import xyz.wagyourtail.unimined.internal.mapping.extension.MixinRemapExtension
import xyz.wagyourtail.unimined.internal.mapping.extension.mixinextra.MixinExtra
import xyz.wagyourtail.unimined.util.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.inject.Inject
import kotlin.io.path.*

abstract class RemapJarTaskImpl @Inject constructor(@get:Internal val provider: MinecraftConfig): RemapJarTask() {

    private var mixinRemapOptions: MixinRemapOptions.() -> Unit by FinalizeOnRead {}

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

    override fun mixinRemap(action: MixinRemapOptions.() -> Unit) {
        mixinRemapOptions = action
    }

    override var allowImplicitWildcards by FinalizeOnRead(false)

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

        val inputFile = provider.mcPatcher.beforeRemapJarTask(this, inputFile.get().asFile.toPath())

        if (path.isEmpty()) {
            project.logger.lifecycle("[Unimined/RemapJar ${this.path}] detected empty remap path, jumping to after remap tasks")
            provider.mcPatcher.afterRemapJarTask(this, inputFile)
            afterRemap(inputFile, inputFile)
            return
        }

        val last = path.last()
        project.logger.lifecycle("[Unimined/RemapJar ${this.path}] remapping output ${inputFile.name} from $devNs/$devFNs to $prodNs")
        project.logger.info("[Unimined/RemapJar ${this.path}]    $devNs -> ${path.joinToString(" -> ") { it.name }}")
        var prevTarget = inputFile
        var prevNamespace = devNs
        var prevPrevNamespace = devFNs
        for (i in path.indices) {
            val step = path[i]
            project.logger.info("[Unimined/RemapJar ${this.path}]    $step")
            val nextTarget = project.buildDir.resolve("tmp").resolve(name).toPath().resolve("${inputFile.nameWithoutExtension}-temp-${step.name}.jar")
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
        project.logger.info("[Unimined/RemapJar ${path}] after remap tasks started ${System.currentTimeMillis()}")
        provider.mcPatcher.afterRemapJarTask(this, prevTarget)
        afterRemap(inputFile, prevTarget)
        project.logger.info("[Unimined/RemapJar ${path}] after remap tasks finished ${System.currentTimeMillis()}")
    }

    private fun afterRemap(inputFile: Path, afterRemapJar: Path) {
        // merge in manifest from input jar
        inputFile.readZipInputStreamFor("META-INF/MANIFEST.MF", false) { inp ->
            // write to temp file
            val inpTmp = project.buildDir.resolve("tmp").resolve(name).toPath().resolve("input-manifest.MF")
            inpTmp.outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
                inp.copyTo(out)
            }
            this.manifest {
                it.from(inpTmp)
            }
        }
        // copy into output
        from(project.zipTree(afterRemapJar))
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
        project.logger.info("[Unimined/RemapJar ${path}] remapping $fromNs -> $toNs (start time: ${System.currentTimeMillis()})")
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
        val betterMixinExtension = MixinRemapExtension(
            project.gradle.startParameter.logLevel,
            allowImplicitWildcards
        )
        betterMixinExtension.enableBaseMixin()
        mixinRemapOptions(betterMixinExtension)
        remapperB.extension(betterMixinExtension)
        provider.minecraftRemapper.tinyRemapperConf(remapperB)
        val remapper = remapperB.build()
        val tag = remapper.createInputTag()
        project.logger.debug("[Unimined/RemapJar ${path}] classpath: ")
        (provider.sourceSet.runtimeClasspath.files.map { it.toPath() }
            .filter { !provider.isMinecraftJar(it) }
            .filter { it.exists() } + listOf(mc))
            .joinToString { "\n[Unimined/RemapJar ${path}]  -  $it" }
            .let { project.logger.debug(it) }
        project.logger.debug("[Unimined/RemapJar ${path}] input: $from")
        betterMixinExtension.readClassPath(remapper,
            *(provider.sourceSet.runtimeClasspath.files.map { it.toPath() }
                .filter { !provider.isMinecraftJar(it) }
                .filter { it.exists() } + listOf(mc))
                .toTypedArray()
        ).thenCompose {
            project.logger.info("[Unimined/RemapJar ${path}] reading input: $from (time: ${System.currentTimeMillis()})")
            betterMixinExtension.readInput(remapper, tag, from)
        }.thenRun {
            project.logger.info("[Unimined/RemapJar ${path}] writing output: $target (time: ${System.currentTimeMillis()})")
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
                            AccessTransformerMinecraftTransformer.AtRemapper(
                                project.logger,
                                remapATToLegacy.getOrElse((provider.mcPatcher as? ForgeLikePatcher<*>)?.remapAtToLegacy == true)!!
                            ),
                        )
                    )
                    remapper.apply(it, tag)
                }
            } catch (e: Exception) {
                target.deleteIfExists()
                throw e
            }
            remapper.finish()
            target.openZipFileSystem(mapOf("mutable" to true)).use {
                betterMixinExtension.insertExtra(tag, it)
            }
            project.logger.info("[Unimined/RemapJar ${path}] remapped $fromNs -> $toNs (end time: ${System.currentTimeMillis()})")
        }.join()
    }

}