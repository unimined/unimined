package xyz.wagyourtail.unimined.api.mapping.mixin

/**
 * @since 1.1.0
 */
interface MixinRemapOptions {

    fun enableMixinExtra()

    fun enableBaseMixin()

    fun enableJarModAgent()

    fun reset()
}