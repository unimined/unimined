package xyz.wagyourtail.unimined

import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists

inline fun <T> consumerApply(crossinline action: T.() -> Unit): (T) -> Unit {
    return { action(it) }
}

fun Path.maybeCreate(): Path {
    if (!this.exists()) {
        this.toFile().mkdirs()
    }
    return this
}

object OSUtils {
    val oSId: String
        get() {
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
            if (osName.contains("darwin") || osName.contains("mac")) {
                return "osx"
            }
            if (osName.contains("win")) {
                return "windows"
            }
            return if (osName.contains("nux")) {
                "linux"
            } else "unknown"
        }
    val osVersion: String
        get() = System.getProperty("os.version")
    val osArch: String
        get() = System.getProperty("os.arch")
}

