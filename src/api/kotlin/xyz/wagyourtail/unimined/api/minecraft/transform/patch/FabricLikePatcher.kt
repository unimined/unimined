package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import java.io.File

/**
 * The class responsible for patching minecraft for fabric.
 * @since 0.2.3
 */
interface FabricLikePatcher: MinecraftPatcher, AccessTransformablePatcher {

    /**
     * 0.5.0 - make var for beta's and other official mapped versions
     * @since 0.2.3
     */
    override var prodNamespace: MappingNamespace

    /**
     * @since 0.5.0
     */
    fun setProdNamespace(namespace: String) {
        prodNamespace = MappingNamespace.getNamespace(namespace)
    }

    /**
     * location of access widener file to apply to the minecraft jar.
     */
    var accessWidener: File?

    /**
     * set the access widener file to apply to the minecraft jar.
     */
    fun setAccessWidener(file: String) {
        accessWidener = File(file)
    }

    fun mergeAws(inputs: List<File>): File
    fun mergeAws(namespace: MappingNamespace, inputs: List<File>): File
    fun mergeAws(output: File, inputs: List<File>): File
    fun mergeAws(output: File, namespace: MappingNamespace, inputs: List<File>): File
}