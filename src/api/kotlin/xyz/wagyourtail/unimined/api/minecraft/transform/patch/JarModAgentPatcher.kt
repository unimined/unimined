package xyz.wagyourtail.unimined.api.minecraft.transform.patch

/**
 * @since 0.5.0
 */
interface JarModAgentPatcher : JarModPatcher {

    /**
     * @since 0.5.0
     * add a transform file for ClassTransform with the java agent.
     */
    var transforms: String?

    /**
     * @since 0.5.0
     * If should do the compile time transforms so normal jar
     * modding will be compatible with the output instead of requiring JarModAgent
     *
     * WARNING: may violate mojang's EULA... use at your own risk. this is not recommended and
     * is only here for legacy reasons and testing.
     */
    @Deprecated("may violate mojang's EULA... use at your own risk. this is not recommended and is only here for legacy reasons and testing.")
    var compileTimeTransforms: Boolean

}