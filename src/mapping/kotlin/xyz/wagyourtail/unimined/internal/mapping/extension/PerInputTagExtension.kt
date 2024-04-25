package xyz.wagyourtail.unimined.internal.mapping.extension

import net.fabricmc.tinyremapper.ClassInstance
import net.fabricmc.tinyremapper.InputTag
import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrEnvironment
import org.objectweb.asm.ClassVisitor
import xyz.wagyourtail.unimined.util.defaultedMapOf
import java.nio.file.FileSystem
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

abstract class PerInputTagExtension<T : PerInputTagExtension.InputTagExtension> : TinyRemapper.Extension {
    object SKIP

    companion object {
        fun getInputTag(cls: TrClass): List<InputTag>? {
            if (cls !is ClassInstance) return null
            // InputTag[] getInputTags()
            ClassInstance::class.java.getDeclaredMethod("getInputTags").apply {
                isAccessible = true
                val arr = (invoke(cls) as Array<InputTag>?) ?: return null
                return arr.toList()
            }
        }
    }

    private val inputTagExtensions: MutableMap<Any?, InputTagExtension> = defaultedMapOf(ConcurrentHashMap()) {
        if (it == SKIP) object : InputTagExtension {
            override val inputTag: InputTag? = null
        }
        else register(it as InputTag)
    }

    protected abstract fun register(tag: InputTag): T

    override fun attach(builder: TinyRemapper.Builder) {
        builder.extraAnalyzeVisitor(::analyzeVisitor)
        builder.extraStateProcessor(::stateProcessor)
        builder.extraPreApplyVisitor(::preApplyVisitor)
        builder.extraPostApplyVisitor(::postApplyVisitor)
    }

    fun readInput(remapper: TinyRemapper, tag: InputTag?, vararg input: Path): CompletableFuture<*> {
        return inputTagExtensions[tag]!!.readInput(remapper, *input)
    }

    fun readClassPath(remapper: TinyRemapper, vararg classpath: Path): CompletableFuture<*> {
        return remapper.readClassPathAsync(*classpath)
//        return CompletableFuture.completedFuture(null)
    }

    fun <T, U> Iterable<T>.reduce(identity: U, reducer: T.(U) -> U): U {
        var acc = identity
        val iter = iterator()
        while (iter.hasNext()) {
            acc = iter.next().reducer(acc)
        }
        return acc
    }

    private fun analyzeVisitor(mrjVersion: Int, className: String, next: ClassVisitor): ClassVisitor {
        return inputTagExtensions.values.toList().reduce(next) { ni -> analyzeVisitor(mrjVersion, className, ni) }
    }

    private fun stateProcessor(environment: TrEnvironment) {
        for (extension in inputTagExtensions.values) {
            extension.stateProcessor(environment)
        }
    }

    private fun preApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor {
        val tags = getInputTag(cls)
        return tags?.reduce(next) { ni -> inputTagExtensions[this]!!.preApplyVisitor(cls, ni) }
            ?: inputTagExtensions[SKIP]!!.preApplyVisitor(cls, next)
    }

    private fun postApplyVisitor(cls: TrClass, next: ClassVisitor): ClassVisitor {
        val tags = getInputTag(cls)
        return tags?.reduce(next) { ni -> inputTagExtensions[this]!!.postApplyVisitor(cls, ni) }
            ?: inputTagExtensions[SKIP]!!.postApplyVisitor(cls, next)
    }

    fun insertExtra(tag: InputTag, fs: FileSystem) {
        inputTagExtensions[tag]!!.insertExtra(fs)
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
