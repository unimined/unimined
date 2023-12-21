package xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit

import net.fabricmc.mappingio.format.PackageRemappingVisitor
import org.gradle.api.Project
import org.w3c.dom.Element
import xyz.wagyourtail.unimined.api.mapping.MappingDepConfig
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.api.minecraft.EnvType
import xyz.wagyourtail.unimined.api.minecraft.patch.bukkit.CraftbukkitPatcher
import xyz.wagyourtail.unimined.api.runs.RunConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.mapping.MappingsProvider
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.internal.minecraft.patch.bukkit.buildtools.BuildToolsExecutor
import xyz.wagyourtail.unimined.util.*
import kotlin.io.path.copyTo

open class CraftbukkitMinecraftTransformer(
    project: Project,
    provider: MinecraftProvider,
    providerName: String = "craftbukkit"
) : AbstractMinecraftTransformer(project, provider, providerName), CraftbukkitPatcher {

    val cache by lazy {
        project.unimined.getLocalCache(provider.sourceSet).resolve("spigot")
    }

    override var loader: String by MustSet()

    val spigotLibraries = project.configurations.maybeCreate("spigotLibraries".withSourceSet(provider.sourceSet)).apply {
        provider.sourceSet.compileClasspath += this
        provider.sourceSet.runtimeClasspath += this
    }

    val executor by LazyMutable {
        BuildToolsExecutor(
            project,
            provider,
            loader,
            cache,
            target
        )
    }

    init {
        unprotectRuntime = true
    }

    override fun beforeMappingsResolve() {
        super.beforeMappingsResolve()
        provider.mappings {
            spigotProd()
        }
    }

    override fun apply() {
        project.configurations.getByName("runtimeOnly".withSourceSet(provider.sourceSet)).dependencies.addAll(
            listOf(
                project.dependencies.create("org.ow2.asm:asm:9.5"),
                project.dependencies.create("org.ow2.asm:asm-commons:9.5"),
                project.dependencies.create("org.ow2.asm:asm-tree:9.5")
            )
        )
    }

    var target: BuildToolsExecutor.BuildTarget by FinalizeOnRead(BuildToolsExecutor.BuildTarget.CRAFTBUKKIT)

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        if (minecraft.envType != EnvType.SERVER) throw IllegalArgumentException("Craftbukkit can only be applied to server jars")

        executor.cloneRepos()
        val outputFile = executor.runBuildTools()

        val deps = executor.targetPom.getElementsByTagName("dependencies").item(0).childNodes

        (0 until deps.length).forEach { i ->
            val it = deps.item(i)
            if (it.nodeName == "dependency") {
                it as Element
                val groupId = it.getElementsByTagName("groupId").item(0).textContent
                val artifactId = it.getElementsByTagName("artifactId").item(0).textContent
                var version = it.getElementsByTagName("version").item(0).textContent
                if (version == "\${project.version}") {
                    version = executor.version
                }
                // skip bukkit/spigot
                if (artifactId == "minecraft-server" || artifactId == "netty-transport-native-epoll") return@forEach
                val dep = project.dependencies.create("$groupId:$artifactId:$version")
                spigotLibraries.dependencies.add(dep)
            }
        }

        // copy output file to
        val patchedJar = MinecraftJar(
            minecraft,
            name = providerName,
            patches = minecraft.patches + listOf(executor.version + "-${executor.buildInfo.name}"),
            mappingNamespace = provider.mappings.getNamespace("spigot_prod"),
        )

        outputFile.copyTo(patchedJar.path, overwrite = true)

        return super.transform(patchedJar)
    }


    private fun MappingsConfig.spigotProd(key: String = "spigot_prod", action: MappingDepConfig.() -> Unit = {}) {
        val provider = MappingsProvider(project, provider, "spigot-prod")
        provider.apply {
            spigotDev(key = "spigot_prod") {
                mapNamespace("spigot_dev", "spigot_prod")
                if (executor.versionInfo.mappingsUrl == null) {
                    forwardVisitor { visitor, _, _ ->
                        PackageRemappingVisitor(
                            visitor,
                            setOf("spigot_prod"),
                            listOf("net/minecraft/server/**" to "net/minecraft/server/${executor.minecraftVersion}")
                        )
                    }
                }
                memberNameReplacer("spigot_prod", "official", setOf("method", "field", "method_arg", "method_var"))
                clearOutputs()
                outputs("spigot_prod", false) { listOf("official") }
            }
        }
        provider.resolveMappingTree()
        mapping(project.dependencies.create(
            project.files(provider.mappingCacheFile()),
        ), key) {
            outputs("spigot_prod", false) {
                if ("spigot_dev" in getNamespaces()) {
                    listOf("official", "spigot_dev")
                } else {
                    listOf("official")
                }
            }
            action()
        }
    }

    override fun applyServerRunTransform(config: RunConfig) {
        super.applyServerRunTransform(config)
        config.mainClass = "org.bukkit.craftbukkit.Main"
    }

}