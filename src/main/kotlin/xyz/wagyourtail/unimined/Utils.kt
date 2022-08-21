package xyz.wagyourtail.unimined

import java.io.IOException
import java.io.PrintStream
import java.io.PrintWriter
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.deleteExisting
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

fun Path.getSha1(): String {
    val digestSha1 = MessageDigest.getInstance("SHA-1")
    inputStream().use {
        digestSha1.update(it.readBytes())
    }
    val hashBytes = digestSha1.digest()
    val hash = hashBytes.joinToString("") { String.format("%02x", it) }
    return hash
}

fun runJarInSubprocess(
    jar: Path,
    vararg args: String,
    mainClass: String? = null,
    workingDir: Path = Paths.get("."),
    env: Map<String, String> = mapOf(),
    wait: Boolean = true,
    jvmArgs: List<String> = listOf()
) : Int? {
    val javaHome = System.getProperty("java.home")
    val javaBin = Paths.get(javaHome, "bin", if (OSUtils.oSId == "windows") "java.exe" else "java")
    if (!javaBin.exists()) {
        throw IllegalStateException("java binary not found at $javaBin")
    }
    val processArgs = if (mainClass == null) {
        arrayOf("-jar", jar.toString())
    } else {
        arrayOf("-cp", jar.toString(), mainClass)
    } + args
    val processBuilder = ProcessBuilder(
        javaBin.toString(),
        *jvmArgs.toTypedArray(),
        *processArgs,
    )
    processBuilder.directory(workingDir.toFile())
    processBuilder.environment().putAll(env)
//    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    System.out.println("Running: ${processBuilder.command().joinToString(" ")}")
    val process = processBuilder.start()
    if (wait) {
        process.waitFor()
        return process.exitValue()
    }
    return null
}

fun Path.deleteRecursively() {
    Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            dir.deleteExisting()
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            file.deleteExisting()
            return FileVisitResult.CONTINUE
        }
    })
}