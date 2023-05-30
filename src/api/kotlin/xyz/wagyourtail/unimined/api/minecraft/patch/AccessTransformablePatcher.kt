package xyz.wagyourtail.unimined.api.minecraft.patch

import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import java.io.File


interface AccessTransformablePatcher {
    fun at2aw(input: String, output: String, namespace: MappingNamespaceTree.Namespace): File
    fun at2aw(input: String, namespace: MappingNamespaceTree.Namespace): File
    fun at2aw(input: String, output: String): File
    fun at2aw(input: String): File
    fun at2aw(input: File): File
    fun at2aw(input: File, namespace: MappingNamespaceTree.Namespace): File
    fun at2aw(input: File, output: File): File
    fun at2aw(input: File, output: File, namespace: MappingNamespaceTree.Namespace): File
}