package xyz.wagyourtail.unimined.internal.mapping.mixin.hard

import net.fabricmc.tinyremapper.extension.mixin.common.Logger
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import net.fabricmc.tinyremapper.extension.mixin.common.data.MxClass
import net.fabricmc.tinyremapper.extension.mixin.hard.data.SoftInterface
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

typealias ClassAnnotationPredicate = (
    descriptor: String,
    visible: Boolean,
    hartTargetRemapper: HardTargetRemappingClassVisitor
) -> Boolean
typealias ClassAnnotationVisitor = (
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    hartTargetRemapper: HardTargetRemappingClassVisitor
) -> AnnotationVisitor

typealias MethodAnnotationPredicate = (
    descriptor: String,
    visible: Boolean,
    methodAccess: Int,
    methodName: String,
    methodDescriptor: String,
    methodSignature: String?,
    methodExceptions: Array<out String>?,
    hartTargetRemapper: HardTargetRemappingClassVisitor
) -> Boolean
typealias MethodAnnotationVisitor = (
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    methodAccess: Int,
    methodName: String,
    methodDescriptor: String,
    methodSignature: String?,
    methodExceptions: Array<out String>?,
    hartTargetRemapper: HardTargetRemappingClassVisitor
) -> AnnotationVisitor

typealias FieldAnnotationPredicate = (
    descriptor: String,
    visible: Boolean,
    access: Int,
    fieldName: String,
    fieldDescriptor: String,
    fieldSignature: String?,
    fieldValue: Any?,
    hartTargetRemapper: HardTargetRemappingClassVisitor
) -> Boolean
typealias FieldAnnotationVisitor = (
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    fieldAccess: Int,
    fieldName: String,
    fieldDescriptor: String,
    fieldSignature: String?,
    fieldValue: Any?,
    hartTargetRemapper: HardTargetRemappingClassVisitor
) -> AnnotationVisitor

class HardTargetRemappingClassVisitor(
    val tasks: MutableList<(CommonData) -> Unit>,
    delegate: ClassVisitor?,
    val existingMappings: Map<String, String>,
    val logger: Logger,
    val onEnd: () -> Unit = {}
) : ClassVisitor(Constant.ASM_VERSION, delegate) {

    lateinit var mxClass: MxClass
    val interfaces: MutableList<SoftInterface> = mutableListOf()

    val targetClasses = mutableSetOf<String>()
    val remap = AtomicBoolean(true)


    val classAnnotationVisitors: MutableList<Pair<ClassAnnotationPredicate, ClassAnnotationVisitor>> = mutableListOf(

    )

    val methodAnnotationVisitors: MutableList<Pair<MethodAnnotationPredicate, MethodAnnotationVisitor>> = mutableListOf(

    )

    val fieldAnnotationVisitors: MutableList<Pair<FieldAnnotationPredicate, FieldAnnotationVisitor>> = mutableListOf(

    )

    fun insertVisitor(visitor: (ClassVisitor) -> ClassVisitor) {
        val old = this.cv
        this.cv = visitor(old)
    }

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String,
        interfaces: Array<out String>?
    ) {
        mxClass = MxClass(name)
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
        if (descriptor != null) {
            for ((predicate, visitor) in classAnnotationVisitors) {
                if (predicate(descriptor, visible, this)) {
                    return visitor(descriptor, visible, super.visitAnnotation(descriptor, visible), this)
                }
            }
        }
        return super.visitAnnotation(descriptor, visible)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        return object : MethodVisitor(Constant.ASM_VERSION, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
                if (descriptor != null) {
                    for ((predicate, visitor) in methodAnnotationVisitors) {
                        if (predicate(descriptor, visible, access, name, descriptor, signature, exceptions, this@HardTargetRemappingClassVisitor)) {
                            return visitor(descriptor, visible, super.visitAnnotation(descriptor, visible), access, name, descriptor, signature, exceptions, this@HardTargetRemappingClassVisitor)
                        }
                    }
                }
                return super.visitAnnotation(descriptor, visible)
            }
        }
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor {
        return object : FieldVisitor(Constant.ASM_VERSION, super.visitField(access, name, descriptor, signature, value)) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
                if (descriptor != null) {
                    for ((predicate, visitor) in fieldAnnotationVisitors) {
                        if (predicate(descriptor, visible, access, name, descriptor, signature, value, this@HardTargetRemappingClassVisitor)) {
                            return visitor(descriptor, visible, super.visitAnnotation(descriptor, visible), access, name, descriptor, signature, value, this@HardTargetRemappingClassVisitor)
                        }
                    }
                }
                return super.visitAnnotation(descriptor, visible)
            }
        }
    }

    override fun visitEnd() {
        super.visitEnd()
        onEnd()
    }

}
