package xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap

import xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.clazz.CTransformerAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.field.CShadowFieldAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.method.*
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor

object JMARefmap {

    fun refmapBuilder(refmapBuilder: RefmapBuilderClassVisitor) {

        refmapBuilder.classAnnotationVisitors.addAll(listOf(
            CTransformerAnnotationVisitor.Companion::shouldVisit to ::CTransformerAnnotationVisitor
        ))

        refmapBuilder.methodAnnotationVisitors.addAll(listOf(
            CInjectAnnotationVisitor.Companion::shouldVisit to ::CInjectAnnotationVisitor,
            CModifyConstantAnnotationVisitor.Companion::shouldVisit to ::CModifyConstantAnnotationVisitor,
            COverrideAnnotationVisitor.Companion::shouldVisit to ::COverrideAnnotationVisitor,
            CRedirectAnnotationVisitor.Companion::shouldVisit to ::CRedirectAnnotationVisitor,
            CShadowMethodAnnotationVisitor.Companion::shouldVisit to ::CShadowMethodAnnotationVisitor,
            CWrapCatchAnnotationVisitor.Companion::shouldVisit to ::CWrapCatchAnnotationVisitor,
        ))

        refmapBuilder.fieldAnnotationVisitors.addAll(listOf(
            CShadowFieldAnnotationVisitor.Companion::shouldVisit to ::CShadowFieldAnnotationVisitor
        ))

    }

}