package xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard

import CShadowFieldAnnotationVisitor
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.DontRemapAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard.annotations.clazz.CTransformerAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard.annotations.method.COverrideAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard.annotations.method.CShadowMethodAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.HardTargetRemappingClassVisitor

object JMAHard {


    fun hardRemapper(hardRemapper: HardTargetRemappingClassVisitor) {

        hardRemapper.insertVisitor {
            DontRemapAnnotationVisitor.DontRemapClassVisitor(Constant.ASM_VERSION, it, hardRemapper.extraData)
        }

        hardRemapper.classAnnotationVisitors.addAll(listOf(
            DontRemapAnnotationVisitor.Companion::shouldVisitHardClass to DontRemapAnnotationVisitor.Companion::visitHardClass,
            CTransformerAnnotationVisitor.Companion::shouldVisit to ::CTransformerAnnotationVisitor
        ))

        hardRemapper.methodAnnotationVisitors.addAll(listOf(
            DontRemapAnnotationVisitor.Companion::shouldVisitHardMethod to DontRemapAnnotationVisitor.Companion::visitHardMethod,
            CShadowMethodAnnotationVisitor.Companion::shouldVisit to ::CShadowMethodAnnotationVisitor,
            COverrideAnnotationVisitor.Companion::shouldVisit to ::COverrideAnnotationVisitor
        ))

        hardRemapper.fieldAnnotationVisitors.addAll(listOf(
            DontRemapAnnotationVisitor.Companion::shouldVisitHardField to DontRemapAnnotationVisitor.Companion::visitHardField,
            CShadowFieldAnnotationVisitor.Companion::shouldVisit to ::CShadowFieldAnnotationVisitor
        ))
    }


}