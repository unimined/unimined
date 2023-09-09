package xyz.wagyourtail.unimined.internal.mapping.extension

import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrEnvironment
import org.objectweb.asm.ClassVisitor
import xyz.wagyourtail.unimined.util.defaultedMapOf
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

abstract class PerInputTagExtension<T : PerInputTagExtension.InputTagExtension> : TinyRemapper.Extension {
    object SKIP

    private val inputTagExtensions: MutableMap<Any?, InputTagExtension> = defaultedMapOf {
        if (it == SKIP) object : InputTagExtension {
            override val inputTag: InputTag? = null
        }
        else register(it as InputTag?)
    }

    private var current: Any? = null

    protected abstract fun register(tag: InputTag?): T

    override fun attach(builder: TinyRemapper.Builder) {
        builder.extraAnalyzeVisitor(::analyzeVisitor)
        builder.extraStateProcessor(::stateProcessor)
        builder.extraPreApplyVisitor(::preApplyVisitor)
        builder.extraPostApplyVisitor(::postApplyVisitor)
    }

    fun setTag(tag: InputTag?) {
        current = tag
    }

    fun skip() {
        current = SKIP
    }

    fun readInput(remapper: TinyRemapper, tag: InputTag?, vararg input: Path): CompletableFuture<*> {
        current = tag
        return inputTagExtensions[current]!!.readInput(remapper, *input)
    }

    fun readClassPath(remapper: TinyRemapper, vararg classpath: Path): CompletableFuture<Void> {
        current = SKIP
        remapper.readClassPath(*classpath)
        return CompletableFuture.completedFuture(null)
    }

    private fun analyzeVisitor(mrjVersion: Int, className: String, next: ClassVisitor): ClassVisitor {
        return inputTagExtensions[current]!!.analyzeVisitor(mrjVersion, className, next)
    }

    private fun stateProcessor(environment: TrEnvironment) {
        for (extension in inputTagExtensions.values) {
            extension.stateProcessor(environment)
        }
    }

    private fun preApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor {
        return inputTagExtensions[current]!!.preApplyVisitor(cls, next)
    }

    private fun postApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor {
        return inputTagExtensions[current]!!.postApplyVisitor(cls, next)
    }

    fun insertExtra(tag: InputTag?, fs: FileSystem) {
        current = tag
        inputTagExtensions[current]!!.insertExtra(fs)
    }

    interface InputTagExtension {

        val inputTag: InputTag?

        fun readInput(remapper: TinyRemapper, vararg input: Path): CompletableFuture<*> {
            return remapper.readInputsAsync(inputTag, *input)
        }

        fun analyzeVisitor(mrjVersion: Int, className: String, next: ClassVisitor): ClassVisitor { return next; }
        fun stateProcessor(environment: TrEnvironment) {}
        fun preApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor { return next; }
        fun postApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor { return next; }

        fun insertExtra(fs: FileSystem) {}

    }
}