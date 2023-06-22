package xyz.wagyourtail.unimined.artifacttransformer

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

abstract class RemapMinecraft : TransformAction<TransformParameters.None> {

    @InputArtifact
    abstract fun getInputArtifact(): Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = getInputArtifact().get().asFile
        
    }

}