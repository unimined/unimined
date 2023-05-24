package xyz.wagyourtail.unimined.api.minecraft.patch

import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import java.io.File


interface AccessTransformablePatcher {
    fun at2aw(input: String, output: String, namespace: MappingNamespace): File
    fun at2aw(input: String, namespace: MappingNamespace): File
    fun at2aw(input: String, output: String): File
    fun at2aw(input: String): File
    fun at2aw(input: File): File
    fun at2aw(input: File, namespace: MappingNamespace): File
    fun at2aw(input: File, output: File): File
    fun at2aw(input: File, output: File, namespace: MappingNamespace): File
}