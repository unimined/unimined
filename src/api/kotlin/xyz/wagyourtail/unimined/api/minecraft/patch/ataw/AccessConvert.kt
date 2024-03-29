package xyz.wagyourtail.unimined.api.minecraft.patch.ataw

import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import java.io.File

/**
 * split from forge/fabric patcher in 1.2.0
 * @since 1.2.0
 */
interface AccessConvert {

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: String): File

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: String, output: String): File

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: File): File

    /**
     * convert access widener to access transformer.
     */
    fun aw2at(input: File, output: File): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: String): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: String, output: String): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: File): File

    /**
     * convert access widener to legacy access transformer (mc version <= 1.7.10).
     */
    fun aw2atLegacy(input: File, output: File): File

    /*
     * merge access wideners to an output file
     */
    fun mergeAws(inputs: List<File>): File

    /*
     * merge access wideners to an output file
     */
    fun mergeAws(namespace: MappingNamespaceTree.Namespace, inputs: List<File>): File

    /*
     * merge access wideners to an output file
     */
    fun mergeAws(output: File, inputs: List<File>): File

    /*
     * merge access wideners to an output file
     */
    fun mergeAws(output: File, namespace: MappingNamespaceTree.Namespace, inputs: List<File>): File

    /**
     * convert access transformer to access widener.
     */
    fun at2aw(input: String, output: String, namespace: MappingNamespaceTree.Namespace): File

    /**
     * convert access transformer to access widener.
     */
    fun at2aw(input: String, namespace: MappingNamespaceTree.Namespace): File

    /**
     * convert access transformer to access widener.
     */
    fun at2aw(input: String, output: String): File

    /**
     * convert access transformer to access widener.
     */
    fun at2aw(input: String): File

    /**
     * convert access transformer to access widener.
     */
    fun at2aw(input: File): File

    /**
     * convert access transformer to access widener.
     */
    fun at2aw(input: File, namespace: MappingNamespaceTree.Namespace): File

    /**
     * convert access transformer to access widener.
     */
    fun at2aw(input: File, output: File): File

    /**
     * convert access transformer to access widener.
     */
    fun at2aw(input: File, output: File, namespace: MappingNamespaceTree.Namespace): File
}