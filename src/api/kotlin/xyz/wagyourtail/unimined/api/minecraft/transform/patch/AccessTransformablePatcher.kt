package xyz.wagyourtail.unimined.api.minecraft.transform.patch

import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import java.io.File

interface AccessTransformablePatcher {
    fun at2aw(input: String, output: String, namespace: MappingNamespace) : File
    fun at2aw(input: String, namespace: MappingNamespace) : File
    fun at2aw(input: String, output: String) : File
    fun at2aw(input: String) : File
    fun at2aw(input: File) : File
    fun at2aw(input: File, namespace: MappingNamespace) : File
    fun at2aw(input: File, output: File) : File
    fun at2aw(input: File, output: File, namespace: MappingNamespace) : File

    fun atLegacy2aw(input: String, output: String, namespace: MappingNamespace) : File
    fun atLegacy2aw(input: String, namespace: MappingNamespace) : File
    fun atLegacy2aw(input: String, output: String) : File
    fun atLegacy2aw(input: String) : File
    fun atLegacy2aw(input: File) : File
    fun atLegacy2aw(input: File, namespace: MappingNamespace) : File
    fun atLegacy2aw(input: File, output: File) : File
    fun atLegacy2aw(input: File, output: File, namespace: MappingNamespace) : File
}