package xyz.wagyourtail.unimined.internal.mapping.mixinextra.annotations.method

import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.RefmapBuilderClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixinextra.MixinExtra

@Suppress("UNUSED_PARAMETER")
class WrapOperationAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    methodAccess: Int,
    methodName: String,
    methodDescriptor: String,
    methodSignature: String?,
    methodExceptions: Array<out String>?,
    refmapBuilder: RefmapBuilderClassVisitor,
)  : ModifyExpressionValueAnnotationVisitor(
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

    override val annotationName: String = "@WrapOperation"

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
            return descriptor == MixinExtra.Annotation.WRAP_OPERATION
        }

    }

}