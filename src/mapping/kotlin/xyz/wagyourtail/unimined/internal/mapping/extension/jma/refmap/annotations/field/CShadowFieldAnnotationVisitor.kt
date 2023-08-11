package xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.field

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.JarModAgent
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor

class CShadowFieldAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    fieldAccess: Int,
    fieldName: String,
    fieldDescriptor: String,
    fieldSignature: String?,
    fieldValue: Any?,
    refmapBuilder: RefmapBuilderClassVisitor
) : CAbstractFieldAnnotationVisitor(
    descriptor,
    visible,
    parent,
    fieldAccess,
    fieldName,
    fieldDescriptor,
    fieldSignature,
    fieldValue,
    refmapBuilder
) {

    override val annotationName: String = "@CShadow"

    companion object{
        fun shouldVisit(
            descriptor: String,
            visible: Boolean,
            fieldAccess: Int,
            fieldName: String,
            fieldDescriptor: String,
            fieldSignature: String?,
            fieldValue: Any?,
            refmapBuilder: RefmapBuilderClassVisitor
        ): Boolean{
            return descriptor == JarModAgent.Annotation.CSHADOW
        }
    }

    override fun visit(name: String?, value: Any) {
        if (name == AnnotationElement.VALUE) {
            targetNames.add(value as String)
        }
        super.visit(name, value)
    }


    override fun getTargetNameAndDescs(targetField: String): Pair<String, Set<String?>> {
        return if (targetField.contains(":")) {
            targetField.substringBefore(":") to setOf(targetField.substringAfter(":"))
        } else {
            targetField to setOf(fieldDescriptor)
        }
    }



}