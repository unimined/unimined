package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import groovy.lang.Closure
import org.jetbrains.annotations.ApiStatus
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import java.nio.file.FileSystem

/**
 * The class responsible for patching minecraft.
 * @see [FabricLikePatcher], [JarModPatcher], [ForgePatcher]
 * @since 0.2.3
 */
interface MinecraftPatcher {

    fun name(): String {
        return this::class.simpleName!!
    }

    /**
     * the namespace to use for the production jar.
     */
    @get:ApiStatus.Internal
    val prodNamespace: MappingNamespace

    /**
     * @since 0.4.2
     */
    @set:ApiStatus.Experimental
    var onMergeFail: (clientNode: ClassNode, serverNode: ClassNode, fs: FileSystem, exception: Exception) -> Unit

    /**
     * @since 0.4.2
     */
    fun setOnMergeFail(closure: Closure<*>) {
        onMergeFail = { clientNode, serverNode, fs, exception ->
            closure.call(clientNode, serverNode, fs, exception)
        }
    }

}