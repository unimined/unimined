package xyz.wagyourtail.unimined.internal.minecraft.task

import net.fabricmc.loom.util.kotlin.KotlinClasspathService
import net.fabricmc.loom.util.kotlin.KotlinRemapperClassloader
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.gradle.api.tasks.Internal
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.mapping.mixin.MixinRemapOptions
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.minecraft.patch.forge.ForgeLikePatcher
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerApplier
import xyz.wagyourtail.unimined.internal.mapping.aw.AccessWidenerApplier
import xyz.wagyourtail.unimined.internal.mapping.extension.MixinRemapExtension
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import xyz.wagyourtail.unimined.util.getField
import xyz.wagyourtail.unimined.util.openZipFileSystem
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists

abstract class RemapJarTaskImpl @Inject constructor(provider: MinecraftConfig):
    AbstractRemapJarTaskImpl(provider), RemapJarTask {

    @get:Internal
    protected var mixinRemapOptions: MixinRemapOptions.() -> Unit by FinalizeOnRead {}

    override fun mixinRemap(action: MixinRemapOptions.() -> Unit) {
        val delegate: FinalizeOnRead<MixinRemapOptions.() -> Unit> = RemapJarTaskImpl::class.getField("mixinRemapOptions")!!.getDelegate(this) as FinalizeOnRead<MixinRemapOptions.() -> Unit>
        val old = delegate.value as MixinRemapOptions.() -> Unit
        mixinRemapOptions = {
            old()
            action()
        }
    }

    override var allowImplicitWildcards by FinalizeOnRead(false)

    init {
        remapATToLegacy.convention(null as Boolean?).finalizeValueOnRead()
    }

    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    override fun doRemap(
        from: Path,
        target: Path,
        fromNs: MappingNamespaceTree.Namespace,
        toNs: MappingNamespaceTree.Namespace,
        classpathList: Array<Path>
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
        project.logger.debug("[Unimined/RemapJar ${path}] input: $from")
        betterMixinExtension.readClassPath(remapper, *classpathList).thenCompose {
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
                            AccessWidenerApplier.AwRemapper(
                                if (fromNs.named) "named" else fromNs.name,
                                if (toNs.named) "named" else toNs.name
                            ),
                            AccessTransformerApplier.AtRemapper(
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
        }.join()
        remapper.finish()
        target.openZipFileSystem(mapOf("mutable" to true)).use {
            betterMixinExtension.insertExtra(tag, it)
        }
        project.logger.info("[Unimined/RemapJar ${path}] remapped $fromNs -> $toNs (end time: ${System.currentTimeMillis()})")
    }

}