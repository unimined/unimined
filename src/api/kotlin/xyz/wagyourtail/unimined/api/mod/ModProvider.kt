package xyz.wagyourtail.unimined.api.mod

import xyz.wagyourtail.unimined.api.UniminedExtension

/**
 * The class responsible for providing mod configurations and remapping them.
 * @since 0.2.3
 */
abstract class ModProvider(val parent: UniminedExtension) {

    /**
     * mod remapper.
     */
    abstract val modRemapper: ModRemapper

}