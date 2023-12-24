package xyz.wagyourtail.unimined.api.source

import xyz.wagyourtail.unimined.api.source.generator.SourceGenerator
import xyz.wagyourtail.unimined.api.source.remapper.SourceRemapper

/**
 * @since 1.2.0
 */
interface SourceConfig {

    /**
     * config for remapping sources
     * @since 1.2.0
     */
    fun configRemap(config: SourceRemapper.() -> Unit)

    /**
     * config for generating sources using a decompiler
     * @since 1.2.0
     */
    fun configGenerator(config: SourceGenerator.() -> Unit)

}