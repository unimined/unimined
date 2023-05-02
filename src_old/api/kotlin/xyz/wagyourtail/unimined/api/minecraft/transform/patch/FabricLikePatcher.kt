package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import java.io.File

/**
 * The class responsible for patching minecraft for fabric.
 * @since 0.2.3
 */
interface FabricLikePatcher: MinecraftPatcher, AccessTransformablePatcher {

    override val prodNamespace: MappingNamespace
        get() = MappingNamespace.INTERMEDIARY

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