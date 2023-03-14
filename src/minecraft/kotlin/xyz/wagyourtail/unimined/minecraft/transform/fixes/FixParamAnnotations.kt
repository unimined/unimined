package xyz.wagyourtail.unimined.minecraft.transform.fixes

import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.*
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.properties.Delegates

object FixParamAnnotations {

    class DontTransformException : Exception()

    fun apply(fs: FileSystem) {
        fs.rootDirectories.forEach { root ->
            Files.walk(root).use { s ->
                for (path in s.filter { it.name.endsWith(".class") }) {
                    val reader = ClassReader(path.readBytes())
                    val writer = ClassWriter(reader, ClassWriter.COMPUTE_MAXS)
                    val visitor = ParameterAnnotationVisitor(writer)
                    try {
                        reader.accept(visitor, ClassReader.EXPAND_FRAMES)
                        path.writeBytes(writer.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
                    } catch (e: DontTransformException) {
                        // do nothing
                    }
                }
            }
        }
    }

    class ParameterAnnotationVisitor(cv: ClassVisitor) : ClassVisitor(Constant.ASM_VERSION, cv) {
        var access by Delegates.notNull<Int>()
        lateinit var name: String
        var outer: String? = null
        val inner = mutableSetOf<InnerClass>()

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            super.visit(version, access, name, signature, superName, interfaces)
            this.access = access
            this.name = name
        }

        override fun visitOuterClass(owner: String, name: String?, descriptor: String?) {
            super.visitOuterClass(owner, name, descriptor)
            outer = name
        }

        override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
            super.visitInnerClass(name, outerName, innerName, access)
            inner.add(InnerClass(name, outerName, innerName, access))
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
            return if (name == "<init>") {
                MethodParameterAnnotationVisitor(visitor, descriptor, expectedSynthetic)
            } else {
                visitor
            }
        }

        private val expectedSynthetic by lazy {
            // check for enum
            if ((access and Opcodes.ACC_ENUM) != 0) {
                return@lazy listOf(Type.getType("Ljava/lang/String;"), Type.INT_TYPE)
            }

            // check for inner class
            val info = inner.firstOrNull {
                it.name == name
            } ?: throw DontTransformException()

            // static
            if ((info.access and (Opcodes.ACC_STATIC or Opcodes.ACC_INTERFACE)) != 0) {
                throw DontTransformException()
            }

            // anonymous
            if (info.innerName == null) {
                throw DontTransformException()
            }

            // not an inner class
            if (info.outerName == null) {
                val idx = name.lastIndexOf('$')
                if (idx == -1) {
                    throw DontTransformException()
                }
                return@lazy listOf(Type.getObjectType(name.substring(0, idx)))
            }

            listOf(Type.getObjectType(info.outerName))
        }

        class MethodParameterAnnotationVisitor(methodVisitor: MethodVisitor, desc: String, private val sythetic: List<Type>) : MethodVisitor(Constant.ASM_VERSION, methodVisitor) {
            private val params = Type.getArgumentTypes(desc).toList()
            private var fixOffset = 0
            override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean) {
                val newParamCount = params.size - sythetic.size
                fixOffset = parameterCount - newParamCount
                super.visitAnnotableParameterCount(newParamCount, visible)
            }

            override fun visitParameterAnnotation(
                parameter: Int,
                descriptor: String?,
                visible: Boolean
            ): AnnotationVisitor? {
                if (parameter < fixOffset) {
                    return null
                }
                return super.visitParameterAnnotation(parameter - fixOffset, descriptor, visible)
            }

        }

        class InnerClass(
            val name: String,
            val outerName: String?,
            val innerName: String?,
            val access: Int
        )
    }
}