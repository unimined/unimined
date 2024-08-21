package xyz.wagyourtail.unimined.internal.minecraft.patch.reindev

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import xyz.wagyourtail.unimined.api.minecraft.patch.reindev.FoxLoaderPatcher
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.mapping.Namespace
import java.io.File
import java.io.IOException
import kotlin.io.path.exists

class ReIndevProvider(project: Project, sourceSet: SourceSet) : MinecraftProvider(project, sourceSet) {

    override val obfuscated = false

    override val minecraftData = ReIndevDownloader(project, this)

    init {
        // Required for the following [2.9.4+legacyfabric.8,) dependency
        project.unimined.legacyFabricMaven()

        replaceLibraryVersion("org.ow2.asm", "asm-all", version = "5.2")
        replaceLibraryVersion("org.lwjgl", version = "2.9.4+legacyfabric.8")
        replaceLibraryVersion("net.java.jinput", "jinput", version = "2.0.9")

        mappings.devNamespace = Namespace("official")
    }

    override val mergedOfficialMinecraftFile: File? by lazy {
        val client = minecraftData.minecraftClient
        if (!client.path.exists()) throw IOException("ReIndev path $client does not exist")
        val server = minecraftData.minecraftServer
        val noTransform = NoTransformReIndevTransformer(project, this)
        if (noTransform.canCombine) noTransform.merge(client, server).path.toFile() else null
    }

    override fun foxLoader(action: FoxLoaderPatcher.() -> Unit) {
        mcPatcher = FoxLoaderMinecraftTransformer(project, this).also {
            patcherAction = {
                action(it as FoxLoaderPatcher)
            }
        }
    }

    override val mavenGroup: String = "net.silveros"

    override val minecraftDepName: String = project.path.replace(":", "_").let { projectPath ->
        "reindev${if (projectPath == "_") "" else projectPath}${if (sourceSet.name == "main") "" else "+"+sourceSet.name}"
    }
}
