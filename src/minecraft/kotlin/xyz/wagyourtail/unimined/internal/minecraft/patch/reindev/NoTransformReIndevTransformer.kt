package xyz.wagyourtail.unimined.internal.minecraft.patch.reindev

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.MinecraftJar
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.AbstractMinecraftTransformer
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.util.*
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.time.Duration

class NoTransformReIndevTransformer(project: Project, provider: ReIndevProvider): AbstractReIndevTransformer(
    project,
    provider,
    "ReIndev-none"
) {

}
