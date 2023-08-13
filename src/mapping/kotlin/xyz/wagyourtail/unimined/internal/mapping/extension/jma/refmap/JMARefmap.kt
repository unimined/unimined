package xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap

import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.DontRemapAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.clazz.CTransformerAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.field.CShadowFieldAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.method.*
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor

object JMARefmap {

    fun refmapBuilder(refmapBuilder: RefmapBuilderClassVisitor) {

        refmapBuilder.insertVisitor {
            DontRemapAnnotationVisitor.DontRemapClassVisitor(Constant.ASM_VERSION, it, refmapBuilder.extraData)
        }

        refmapBuilder.classAnnotationVisitors.addAll(listOf(
            DontRemapAnnotationVisitor.Companion::shouldVisitSoftClass to DontRemapAnnotationVisitor.Companion::visitSoftClass,
            CTransformerAnnotationVisitor.Companion::shouldVisit to ::CTransformerAnnotationVisitor
        ))

        refmapBuilder.methodAnnotationVisitors.addAll(listOf(
            DontRemapAnnotationVisitor.Companion::shouldVisitSoftMethod to DontRemapAnnotationVisitor.Companion::visitSoftMethod,
            CInjectAnnotationVisitor.Companion::shouldVisit to ::CInjectAnnotationVisitor,
            CModifyConstantAnnotationVisitor.Companion::shouldVisit to ::CModifyConstantAnnotationVisitor,
            COverrideAnnotationVisitor.Companion::shouldVisit to ::COverrideAnnotationVisitor,
            CRedirectAnnotationVisitor.Companion::shouldVisit to ::CRedirectAnnotationVisitor,
            CShadowMethodAnnotationVisitor.Companion::shouldVisit to ::CShadowMethodAnnotationVisitor,
            CWrapCatchAnnotationVisitor.Companion::shouldVisit to ::CWrapCatchAnnotationVisitor,
        ))

        refmapBuilder.fieldAnnotationVisitors.addAll(listOf(
            DontRemapAnnotationVisitor.Companion::shouldVisitSoftField to DontRemapAnnotationVisitor.Companion::visitSoftField,
            CShadowFieldAnnotationVisitor.Companion::shouldVisit to ::CShadowFieldAnnotationVisitor
        ))

    }

}