package xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor
import java.util.concurrent.atomic.AtomicBoolean

class SliceAnnotationVisitor(parent: AnnotationVisitor?, val remap: AtomicBoolean, private val refmapBuilder: RefmapBuilderClassVisitor) : AnnotationVisitor(Constant.ASM_VERSION, parent) {

    override fun visitAnnotation(name: String, descriptor: String): AnnotationVisitor {
        return if (name == AnnotationElement.FROM || name == AnnotationElement.TO) {
            AtAnnotationVisitor(super.visitAnnotation(name, descriptor), remap, refmapBuilder)
        } else {
            super.visitAnnotation(name, descriptor)
        }
    }

}
