package xyz.wagyourtail.unimined.internal.mapping.extension

import org.objectweb.asm.AnnotationVisitor

class ArrayVisitorWrapper(
    val api: Int,
    delegate: AnnotationVisitor?,
    val delegateCreator: (AnnotationVisitor?) -> AnnotationVisitor
): AnnotationVisitor(api, delegate) {
    override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor {
        return delegateCreator(super.visitAnnotation(name, descriptor))
    }
}