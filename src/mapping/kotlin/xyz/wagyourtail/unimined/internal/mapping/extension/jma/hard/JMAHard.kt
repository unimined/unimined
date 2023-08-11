package xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard

import CShadowFieldAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard.annotations.clazz.CTransformerAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard.annotations.method.COverrideAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard.annotations.method.CShadowMethodAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.HardTargetRemappingClassVisitor

object JMAHard {


    fun hardRemapper(hardRemapper: HardTargetRemappingClassVisitor) {

        hardRemapper.classAnnotationVisitors.add(
            CTransformerAnnotationVisitor.Companion::shouldVisit to ::CTransformerAnnotationVisitor
        )

        hardRemapper.methodAnnotationVisitors.addAll(listOf(
            CShadowMethodAnnotationVisitor.Companion::shouldVisit to ::CShadowMethodAnnotationVisitor,
            COverrideAnnotationVisitor.Companion::shouldVisit to ::COverrideAnnotationVisitor
        ))

        hardRemapper.fieldAnnotationVisitors.addAll(listOf(
            CShadowFieldAnnotationVisitor.Companion::shouldVisit to ::CShadowFieldAnnotationVisitor
        ))
    }


}