package xyz.wagyourtail.unimined.internal.mapping.extension.mixinextra.annotations.method

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.ArrayVisitorWrapper
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations.AtAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations.SliceAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations.method.AbstractMethodAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixinextra.MixinExtra

@Suppress("UNUSED_PARAMETER")
open class ModifyExpressionValueAnnotationVisitor(
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

    override val annotationName: String = "@ModifyExpressionValue"

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
            return descriptor == MixinExtra.Annotation.MODIFY_EXPRESSION_VALUE
        }
    }

    override fun visitArray(name: String): AnnotationVisitor {
        val delegate = super.visitArray(name)
        return when (name) {
            AnnotationElement.AT -> {
                ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { AtAnnotationVisitor(it, remap, refmapBuilder) }
            }

            AnnotationElement.SLICE -> {
                ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { SliceAnnotationVisitor(it, remap, refmapBuilder) }
            }
            AnnotationElement.METHOD -> {
                object: AnnotationVisitor(Constant.ASM_VERSION, delegate) {
                    override fun visit(name: String?, value: Any) {
                        super.visit(name, value)
                        targetNames.add(value as String)
                    }
                }
            }
            else -> {
                delegate
            }
        }
    }



}