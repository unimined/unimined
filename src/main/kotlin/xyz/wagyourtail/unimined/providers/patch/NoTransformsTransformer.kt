package xyz.wagyourtail.unimined.providers.patch

import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier
import org.gradle.api.Project
import xyz.wagyourtail.unimined.providers.minecraft.MinecraftProvider
import java.io.File

class NoTransformsTransformer(project: Project, provider: MinecraftProvider) : AbstractMinecraftTransformer(
    project,
    provider
) {


    override fun transform(artifact: ArtifactIdentifier, file: File): File {
        return file
    }
}
