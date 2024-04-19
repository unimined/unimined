package xyz.wagyourtail.unimined.internal.minecraft.patch.access

import org.gradle.api.Project
import xyz.wagyourtail.unimined.api.mapping.MappingNamespaceTree
import xyz.wagyourtail.unimined.api.minecraft.patch.ataw.AccessConvert
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerApplier
import xyz.wagyourtail.unimined.internal.mapping.aw.AccessWidenerApplier
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.util.withSourceSet
import java.io.File

class AccessConvertImpl(val project: Project, val provider: MinecraftProvider) : AccessConvert {

    override fun aw2at(input: String): File = aw2at(File(input))

    override fun aw2at(input: String, output: String) = aw2at(File(input), File(output))

    override fun aw2at(input: File) = aw2at(
        input,
        provider.sourceSet.resources.srcDirs.first().resolve("META-INF/accesstransformer.cfg")
    )

    override fun aw2at(input: File, output: File): File {
        return AccessTransformerApplier.aw2at(input.toPath(), output.toPath()).toFile()
    }

    override fun aw2atLegacy(input: String): File = aw2atLegacy(File(input))

    override fun aw2atLegacy(input: String, output: String) = aw2atLegacy(File(input), File(output))

    override fun aw2atLegacy(input: File) = aw2atLegacy(
        input,
        provider.sourceSet.resources.srcDirs.first().resolve("META-INF/accesstransformer.cfg")
    )

    override fun aw2atLegacy(input: File, output: File): File {
        return AccessTransformerApplier.aw2at(input.toPath(), output.toPath(), true).toFile()
    }

    override fun at2aw(input: String, output: String, namespace: MappingNamespaceTree.Namespace) =
        at2aw(File(input), File(output), namespace)

    override fun at2aw(input: String, namespace: MappingNamespaceTree.Namespace) = at2aw(File(input), namespace)
    override fun at2aw(input: String, output: String) = at2aw(File(input), File(output))
    override fun at2aw(input: String) = at2aw(File(input))
    override fun at2aw(input: File) = at2aw(input, provider.mappings.devNamespace)
    override fun at2aw(input: File, namespace: MappingNamespaceTree.Namespace) = at2aw(
        input,
        provider.sourceSet.resources.srcDirs.first().resolve("${project.name.withSourceSet(provider.sourceSet)}.accesswidener"),
        namespace
    )

    override fun at2aw(input: File, output: File) = at2aw(input, output, provider.mappings.devNamespace)
    override fun at2aw(input: File, output: File, namespace: MappingNamespaceTree.Namespace): File {
        return AccessTransformerApplier.at2aw(
            input.toPath(),
            output.toPath(),
            namespace.name,
            provider.mappings.mappingTree,
            project.logger
        ).toFile()
    }

    override fun mergeAws(inputs: List<File>): File {
        return mergeAws(
            provider.mappings.devNamespace,
            inputs
        )
    }

    override fun mergeAws(namespace: MappingNamespaceTree.Namespace, inputs: List<File>): File {
        return mergeAws(
            provider.sourceSet.resources.srcDirs.first().resolve("${project.name.withSourceSet(provider.sourceSet)}.accesswidener"),
            namespace,
            inputs
        )
    }

    override fun mergeAws(output: File, inputs: List<File>): File {
        return mergeAws(output, provider.mappings.devNamespace, inputs)
    }

    override fun mergeAws(output: File, namespace: MappingNamespaceTree.Namespace, inputs: List<File>): File {
        return AccessWidenerApplier.mergeAws(
            inputs.map { it.toPath() },
            output.toPath(),
            namespace,
            provider.mappings,
            provider
        ).toFile()
    }

}