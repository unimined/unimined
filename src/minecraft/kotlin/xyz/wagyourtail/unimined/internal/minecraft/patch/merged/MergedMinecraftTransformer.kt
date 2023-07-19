package xyz.wagyourtail.unimined.internal.minecraft.patch.merged

import org.gradle.api.Project
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.api.minecraft.patch.*
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.BabricMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.LegacyFabricMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.OfficialFabricMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.QuiltMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.ForgeLikeMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.MinecraftForgeMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.forge.NeoForgedMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod.JarModAgentMinecraftTransformer
import java.nio.file.FileSystem

class MergedMinecraftTransformer(project: Project, provider: MinecraftProvider): AbstractMinecraftTransformer(project, provider, "merged"), MergedPatcher {

    val patchers = mutableListOf<AbstractMinecraftTransformer>()

    override var onMergeFail: (clientNode: ClassNode, serverNode: ClassNode, fs: FileSystem, exception: Exception) -> Unit
        get() = patchers.first().onMergeFail
        set(value) {
            patchers.first().onMergeFail = value
        }

    override var canCombine: Boolean
        get() = patchers.first().canCombine
        set(value) {
            patchers.forEach { it.canCombine = value }
        }

    override fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar): MinecraftJar {
        val mc = patchers.first().merge(clientjar, serverjar)
        if (mc.mappingNamespace != provider.mappings.OFFICIAL) {
            // remap back to official
            return provider.minecraftRemapper.provide(mc, provider.mappings.OFFICIAL, provider.mappings.OFFICIAL)
        }
        return mc
    }

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        return patchers.scan(minecraft) {
            acc, patcher -> patcher.transform(acc)
        }.last()
    }

    override fun applyExtraLaunches() {
        patchers.forEach { it.applyExtraLaunches() }
    }

    override fun applyClientRunTransform(config: RunConfig) {
        patchers.forEach { it.applyClientRunTransform(config) }
    }

    override fun applyServerRunTransform(config: RunConfig) {
        patchers.forEach { it.applyServerRunTransform(config) }
    }

    override fun apply() {
        patchers.forEach { it.apply() }
    }

    @Deprecated("use prodNamespace instead", replaceWith = ReplaceWith("prodNamespace"))
    override fun setProdNamespace(namespace: String) {
        prodNamespace = provider.mappings.getNamespace(namespace)
    }

    override fun prodNamespace(namespace: String) {
        prodNamespace = provider.mappings.getNamespace(namespace)
    }

    override fun fabric(action: FabricLikePatcher.() -> Unit) {
        val fabric = OfficialFabricMinecraftTransformer(project, provider)
        fabric.action()
        patchers.add(fabric)
    }

    override fun legacyFabric(action: FabricLikePatcher.() -> Unit) {
        val fabric = LegacyFabricMinecraftTransformer(project, provider)
        fabric.action()
        patchers.add(fabric)
    }

    override fun babric(action: FabricLikePatcher.() -> Unit) {
        val fabric = BabricMinecraftTransformer(project, provider)
        fabric.action()
        patchers.add(fabric)
    }

    override fun quilt(action: FabricLikePatcher.() -> Unit) {
        val fabric = QuiltMinecraftTransformer(project, provider)
        fabric.action()
        patchers.add(fabric)
    }

    @Deprecated("Please specify which forge.", replaceWith = ReplaceWith("minecraftForge(action)"))
    override fun forge(action: ForgeLikePatcher.() -> Unit) {
        minecraftForge(action)
    }

    override fun minecraftForge(action: MinecraftForgePatcher.() -> Unit) {
        val forge = MinecraftForgeMinecraftTransformer(project, provider)
        forge.action()
        patchers.add(forge)
    }

    override fun neoForged(action: NeoForgedPatcher.() -> Unit) {
        val forge = NeoForgedMinecraftTransformer(project, provider)
        forge.action()
        patchers.add(forge)
    }

    override fun jarMod(action: JarModAgentPatcher.() -> Unit) {
        val jarMod = JarModAgentMinecraftTransformer(project, provider)
        jarMod.action()
        patchers.add(jarMod)
    }


}