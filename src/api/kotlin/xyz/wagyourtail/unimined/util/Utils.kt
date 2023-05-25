package xyz.wagyourtail.unimined.util

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream

inline fun <T, U> consumerApply(crossinline action: T.() -> U): (T) -> U {
    return { action(it) }
}

fun Configuration.getFile(dep: Dependency, extension: Regex): File {
    resolve()
    return files(dep).first { it.extension.matches(extension) }
}

fun Configuration.getFile(dep: Dependency, extension: String = "jar"): File {
    resolve()
    return files(dep).first { it.extension == extension }
}

fun URI.stream(): InputStream {
    val conn = toURL().openConnection()
    conn.setRequestProperty("User-Agent", "Wagyourtail/Unimined 1.0 (<wagyourtail@wagyourtal.xyz>)")
    return conn.getInputStream()
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

    val osArchNum: String
        get() = when (osArch) {
            "x86" -> "32"
            "i386" -> "32"
            "i686" -> "32"
            "amd64" -> "64"
            "x86_64" -> "64"
            else -> "unknown"
        }
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
    return hashBytes.joinToString("") { String.format("%02x", it) }
}


//fun runJarInSubprocess(
//    jar: Path,
//    vararg args: String,
//    mainClass: String? = null,
//    workingDir: Path = Paths.get("."),
//    env: Map<String, String> = mapOf(),
//    wait: Boolean = true,
//    jvmArgs: List<String> = listOf()
//): Int? {
//    val javaHome = System.getProperty("java.home")
//    val javaBin = Paths.get(javaHome, "bin", if (OSUtils.oSId == "windows") "java.exe" else "java")
//    if (!javaBin.exists()) {
//        throw IllegalStateException("java binary not found at $javaBin")
//    }
//    val processArgs = if (mainClass == null) {
//        arrayOf("-jar", jar.toString())
//    } else {
//        arrayOf("-cp", jar.toString(), mainClass)
//    } + args
//    val processBuilder = ProcessBuilder(
//        javaBin.toString(),
//        *jvmArgs.toTypedArray(),
//        *processArgs,
//    )
//
//    val logger = LoggerFactory.getLogger(UniminedExtension::class.java);
//
//    processBuilder.directory(workingDir.toFile())
//    processBuilder.environment().putAll(env)
//
//    logger.info("Running: ${processBuilder.command().joinToString(" ")}")
//    val process = processBuilder.start()
//
//    val inputStream = process.inputStream
//    val errorStream = process.errorStream
//
//    val outputThread = Thread {
//        inputStream.copyTo(object : OutputStream() {
//            // buffer and write lines
//            private var line: String? = null
//
//            override fun write(b: Int) {
//                if (b == '\r'.toInt()) {
//                    return
//                }
//                if (b == '\n'.toInt()) {
//                    logger.info(line)
//                    line = null
//                } else {
//                    line = (line ?: "") + b.toChar()
//                }
//            }
//        })
//    }
//
//    val errorThread = Thread {
//        errorStream.copyTo(object : OutputStream() {
//            // buffer and write lines
//            private var line: String? = null
//
//            override fun write(b: Int) {
//                if (b == '\r'.toInt()) {
//                    return
//                }
//                if (b == '\n'.toInt()) {
//                    logger.error(line)
//                    line = null
//                } else {
//                    line = (line ?: "") + b.toChar()
//                }
//            }
//        })
//    }
//
//    outputThread.start()
//    errorThread.start()
//
//    if (wait) {
//        process.waitFor()
//        return process.exitValue()
//    }
//    return null
//}

fun Path.deleteRecursively() {
    Files.walkFileTree(this, object: SimpleFileVisitor<Path>() {
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

fun Path.forEachFile(action: (Path) -> Unit) {
    Files.walkFileTree(this, object: SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            action(file)
            return FileVisitResult.CONTINUE
        }
    })
}

fun ByteArray.toHex() = joinToString(separator = "") { byte -> "%02x".format(byte) }

fun <T> Optional<T>.orElse(invoke: () -> Optional<T>): Optional<T> {
    return if (isPresent) {
        this
    } else {
        invoke()
    }
}

fun getTempFilePath(prefix: String, suffix: String): Path {
    return Files.createTempFile(prefix, suffix).apply {
        deleteExisting()
    }
}

operator fun StringBuilder.plusAssign(other: String) {
    append(other)
}