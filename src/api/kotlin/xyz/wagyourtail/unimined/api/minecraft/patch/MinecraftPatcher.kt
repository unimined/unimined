package xyz.wagyourtail.unimined.api.minecraft.patch

import groovy.lang.Closure
import org.jetbrains.annotations.ApiStatus
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import java.nio.file.FileSystem
import java.nio.file.Path

/**
 * The class responsible for patching minecraft.
 * @see [FabricLikePatcher], [JarModPatcher], [ForgeLikePatcher]
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
    val prodNamespace: MappingNamespaceTree.Namespace

    /**
     * @since 0.4.2
     */
    @set:ApiStatus.Experimental
    var onMergeFail: (clientNode: ClassNode, serverNode: ClassNode, fs: FileSystem, exception: Exception) -> Unit

    /**
     * @since 0.4.2
     */
    @ApiStatus.Experimental
    fun setOnMergeFail(closure: Closure<*>) {
        onMergeFail = { clientNode, serverNode, fs, exception ->
            closure.call(clientNode, serverNode, fs, exception)
        }
    }

    @ApiStatus.Internal
    fun beforeRemapJarTask(remapJarTask: RemapJarTask, input: Path): Path

    @ApiStatus.Internal
    fun afterRemapJarTask(remapJarTask: RemapJarTask, output: Path)

    @get:ApiStatus.Internal
    @set:ApiStatus.Experimental
    var canCombine: Boolean

    @get:ApiStatus.Internal
    @set:ApiStatus.Experimental
    var unprotectRuntime: Boolean

    @ApiStatus.Internal
    fun configureRemapJar(task: RemapJarTask)
}