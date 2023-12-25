package xyz.wagyourtail.unimined.internal.source

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.source.SourceConfig
import xyz.wagyourtail.unimined.api.source.generator.SourceGenerator
import xyz.wagyourtail.unimined.api.source.remapper.SourceRemapper
import xyz.wagyourtail.unimined.internal.source.generator.SourceGeneratorImpl
import xyz.wagyourtail.unimined.internal.source.remapper.SourceRemapperImpl

class SourceProvider(val project: Project, val minecraft: MinecraftConfig) : SourceConfig {

    override val sourceRemapper = SourceRemapperImpl(project, this)
    override val sourceGenerator = SourceGeneratorImpl(project, this)

    override fun configRemap(config: SourceRemapper.() -> Unit) {
        sourceRemapper.config()
    }

    override fun configGenerator(config: SourceGenerator.() -> Unit) {
        sourceGenerator.config()
    }

}
