package xyz.wagyourtail.unimined.internal.minecraft.patch.fabric

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftRemapper
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.util.FinalizeOnRead

class BabricMinecraftTransformer(project: Project, provider: MinecraftProvider): FabricMinecraftTransformer(project, provider) {

    override var canCombine: Boolean by FinalizeOnRead(true)

    override fun addIntermediaryMappings() {
        provider.mappings {
            babricIntermediary()
        }
    }

    override fun loader(dep: Any, action: Dependency.() -> Unit) {
        fabric.dependencies.add(
            (if (dep is String && !dep.contains(":")) {
                project.dependencies.create("babric:fabric-loader:$dep")
            } else project.dependencies.create(dep)).apply(action)
        )
    }

    override fun merge(clientjar: MinecraftJar, serverjar: MinecraftJar): MinecraftJar {
        val INTERMEDIARY = provider.mappings.getNamespace("intermediary")
        val CLIENT = provider.mappings.getNamespace("client")
        val SERVER = provider.mappings.getNamespace("server")
        val clientJarFixed = MinecraftJar(
            clientjar.parentPath,
            clientjar.name,
            clientjar.envType,
            clientjar.version,
            clientjar.patches,
            CLIENT,
            CLIENT,
            clientjar.awOrAt,
            clientjar.extension,
            clientjar.path
        )
        val serverJarFixed = MinecraftJar(
            serverjar.parentPath,
            serverjar.name,
            serverjar.envType,
            serverjar.version,
            serverjar.patches,
            SERVER,
            SERVER,
            serverjar.awOrAt,
            serverjar.extension,
            serverjar.path
        )
        val intermediaryClientJar = provider.minecraftRemapper.provide(clientJarFixed, INTERMEDIARY, CLIENT)
        val intermediaryServerJar = provider.minecraftRemapper.provide(serverJarFixed, INTERMEDIARY, SERVER)
        return super.merge(intermediaryClientJar, intermediaryServerJar)
    }

    override fun addMavens() {
        super.addMavens()
        project.unimined.babricMaven()
    }

    override val includeGlobs: List<String>
        get() = super.includeGlobs + "argo/**"
}