package xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations.method

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.ArrayVisitorWrapper
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations.AtAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations.DescAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations.SliceAnnotationVisitor

@Suppress("UNUSED_PARAMETER")
open class ModifyArgAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    methodAccess: Int,
    methodName: String,
    methodDescriptor: String,
    methodSignature: String?,
    methodExceptions: Array<out String>?,
    refmapBuilder: RefmapBuilderClassVisitor,
)  : AbstractMethodAnnotationVisitor(
    descriptor,
    visible,
    parent,
    methodAccess,
    methodName,
    methodDescriptor,
    methodSignature,
    methodExceptions,
    refmapBuilder
) {

    override val annotationName: String = "@ModifyArg"

    companion object {
        fun shouldVisit(
            descriptor: String,
            visible: Boolean,
            methodAccess: Int,
            methodName: String,
            methodDescriptor: String,
            methodSignature: String?,
            methodExceptions: Array<out String>?,
            refmapBuilder: RefmapBuilderClassVisitor
        ): Boolean {
            return descriptor == Annotation.MODIFY_ARG
        }
    }


    override fun visitAnnotation(name: String, descriptor: String): AnnotationVisitor {
        return when (name) {
            AnnotationElement.AT -> {
                AtAnnotationVisitor(super.visitAnnotation(name, descriptor), remap, refmapBuilder)
            }

            AnnotationElement.SLICE -> {
                SliceAnnotationVisitor(super.visitAnnotation(name, descriptor), remap, refmapBuilder)
            }

            else -> {
                super.visitAnnotation(name, descriptor)
            }
        }
    }

    override fun visitArray(name: String): AnnotationVisitor {
        return when (name) {
            AnnotationElement.TARGET -> {
                ArrayVisitorWrapper(Constant.ASM_VERSION, super.visitArray(name)) {
                    DescAnnotationVisitor(it, remap, refmapBuilder)
                }
            }

            AnnotationElement.METHOD -> {
                object: AnnotationVisitor(Constant.ASM_VERSION, super.visitArray(name)) {
                    override fun visit(name: String?, value: Any) {
                        super.visit(name, value)
                        targetNames.add(value as String)
                    }
                }
            }

            else -> {
                super.visitArray(name)
            }
        }
    }

}
