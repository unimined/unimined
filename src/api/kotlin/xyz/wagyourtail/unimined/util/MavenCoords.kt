package xyz.wagyourtail.unimined.util

import kotlin.jvm.JvmInline

@JvmInline
value class MavenCoords(val value: String) {

    constructor(group: String, artifact: String, version: String, classifier: String? = null, extension: String? = null): this(buildString {
        append(group)
        append(':')
        append(artifact)
        append(':')
        append(version)
        if (classifier != null) {
            append(':')
            append(classifier)
        }
        if (extension != null) {
            append('@')
            append(extension)
        }
    })

    val parts: List<String>
        get() = value.split(":")

    val group: String
        get() = parts[0]

    val artifact: String
        get() = parts[1]

    val version: String
        get() = parts[2]

    val classifier: String?
        get() = parts.getOrNull(3)

    val extension: String
        get() = value.substringAfterLast('@', "jar")


    val fileName: String
        get() = buildString {
        append(artifact)
        append('-')
        append(version)
        if (classifier != null) {
            append('-')
            append(classifier)
        }
        append('.')
        append(extension)
    }

    override fun toString() = value

}