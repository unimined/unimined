package xyz.wagyourtail.unimined

import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream

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

fun testSha1(size: Long, sha1: String, path: Path): Boolean {
    if (path.exists()) {
        if (path.fileSize() == size || size == -1L) {
            if (sha1.isEmpty()) {
                return true
            }
            val digestSha1 = MessageDigest.getInstance("SHA-1")
            path.inputStream().use {
                digestSha1.update(it.readBytes())
            }
            val hashBytes = digestSha1.digest()
            val hash = hashBytes.joinToString("") { String.format("%02x", it) }
            if (hash.equals(sha1, ignoreCase = true)) {
                return true
            }
        }
    }
    return false
}
