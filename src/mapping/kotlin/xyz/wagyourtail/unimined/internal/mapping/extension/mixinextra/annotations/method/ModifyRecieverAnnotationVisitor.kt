package xyz.wagyourtail.unimined.internal.mapping.extension.mixinextra.annotations.method

import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixinextra.MixinExtra

@Suppress("UNUSED_PARAMETER")
class ModifyRecieverAnnotationVisitor(
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

    override val annotationName: String = "@ModifyReciever"

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
            return descriptor == MixinExtra.Annotation.MODIFY_RECIEVER
        }

    }

}