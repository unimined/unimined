package xyz.wagyourtail.unimined.internal.minecraft.task

import kotlinx.coroutines.runBlocking
import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import xyz.wagyourtail.unimined.api.mapping.mixin.MixinRemapOptions
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.patch.forge.ForgeLikePatcher
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerApplier
import xyz.wagyourtail.unimined.internal.mapping.aw.AccessWidenerApplier
import xyz.wagyourtail.unimined.internal.mapping.extension.MixinRemapExtension
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.util.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.inject.Inject
import kotlin.io.path.*

abstract class RemapJarTaskImpl @Inject constructor(@get:Internal val provider: MinecraftConfig): RemapJarTask() {

    private var mixinRemapOptions: MixinRemapOptions.() -> Unit by FinalizeOnRead {}

    override fun devNamespace(namespace: String) {
        val delegate: FinalizeOnRead<Namespace> = RemapJarTask::class.getField("devNamespace")!!.getDelegate(this) as FinalizeOnRead<Namespace>
        delegate.setValueIntl(LazyMutable { Namespace(namespace) })
    }

    override fun prodNamespace(namespace: String) {
        val delegate: FinalizeOnRead<Namespace> = RemapJarTask::class.getField("prodNamespace")!!.getDelegate(this) as FinalizeOnRead<Namespace>
        delegate.setValueIntl(LazyMutable { Namespace(namespace) })
    }

    override fun mixinRemap(action: MixinRemapOptions.() -> Unit) {
        val delegate: FinalizeOnRead<MixinRemapOptions.() -> Unit> = RemapJarTaskImpl::class.getField("mixinRemapOptions")!!.getDelegate(this) as FinalizeOnRead<MixinRemapOptions.() -> Unit>
        val old = delegate.value as MixinRemapOptions.() -> Unit
        mixinRemapOptions = {
            old()
            action()
        }
    }

    override var allowImplicitWildcards by FinalizeOnRead(false)

    @TaskAction
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    fun run() {
        val prodNs = prodNamespace ?: provider.mcPatcher.prodNamespace!!
        val devNs = devNamespace ?: provider.mappings.devNamespace!!

        val inputFile = provider.mcPatcher.beforeRemapJarTask(this, inputFile.get().asFile.toPath())

        if (devNs == prodNs) {
            project.logger.lifecycle("[Unimined/RemapJar ${this.path}] detected empty remap path, jumping to after remap tasks")
            provider.mcPatcher.afterRemapJarTask(this, inputFile)
            afterRemap(inputFile)
            return
        }

        project.logger.lifecycle("[Unimined/RemapJar ${this.path}] remapping output ${inputFile.name} from $devNs to $prodNs")
        val prodMapped = temporaryDir.toPath().resolve("${inputFile.nameWithoutExtension}-temp-${prodNs}.jar")
        prodMapped.deleteIfExists()

        val mc = provider.getMinecraft(devNs)

        val classpath = provider.mods.getClasspathAs(
            devNs,
            provider.sourceSet.compileClasspath.files
        ).map { it.toPath() }.filter { it.exists() && !provider.isMinecraftJar(it) }

        project.logger.debug("[Unimined/RemapJar ${path}] classpath: ")
        classpath.forEach {
            project.logger.debug("[Unimined/RemapJar ${path}]    $it")
        }

        remapToInternal(inputFile, prodMapped, devNs, prodNs, (classpath + listOf(mc)).toTypedArray())

        project.logger.info("[Unimined/RemapJar ${path}] after remap tasks started ${System.currentTimeMillis()}")
        provider.mcPatcher.afterRemapJarTask(this, prodMapped)
        afterRemap(prodMapped)
        project.logger.info("[Unimined/RemapJar ${path}] after remap tasks finished ${System.currentTimeMillis()}")
    }

    private fun afterRemap(afterRemapJar: Path) {
        // merge in manifest from input jar
        afterRemapJar.readZipInputStreamFor("META-INF/MANIFEST.MF", false) { inp ->
            // write to temp file
            val inpTmp = temporaryDir.toPath().resolve("input-manifest.MF")
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
        fromNs: Namespace,
        toNs: Namespace,
        classpathList: Array<Path>
    ) = runBlocking {
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
        project.logger.debug("[Unimined/RemapJar ${path}] input: $from")
        betterMixinExtension.readClassPath(remapper, *classpathList).thenCompose {
            project.logger.info("[Unimined/RemapJar ${path}] reading input: $from (time: ${System.currentTimeMillis()})")
            betterMixinExtension.readInput(remapper, tag, from)
        }.thenRun {
            project.logger.info("[Unimined/RemapJar ${path}] writing output: $target (time: ${System.currentTimeMillis()})")
            target.parent.createDirectories()
            try {
                runBlocking {
                    OutputConsumerPath.Builder(target).build().use {
                        it.addNonClassFiles(
                            from,
                            remapper,
                            listOf(
                                AccessWidenerApplier.AwRemapper(
                                    AccessWidenerApplier.nsName(provider.mappings, fromNs),
                                    AccessWidenerApplier.nsName(provider.mappings, toNs),
                                ),
                                AccessTransformerApplier.AtRemapper(
                                    project.logger,
                                    fromNs,
                                    toNs,
                                    isLegacy = remapATToLegacy.getOrElse((provider.mcPatcher as? ForgeLikePatcher<*>)?.remapAtToLegacy == true)!!,
                                    mappings = provider.mappings.resolve()
                                ),
                            )
                        )
                        remapper.apply(it, tag)
                    }
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