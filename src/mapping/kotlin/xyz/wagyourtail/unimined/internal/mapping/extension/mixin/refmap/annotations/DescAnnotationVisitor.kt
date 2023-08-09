package xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations

import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("UNUSED_PARAMETER")
class DescAnnotationVisitor (parent: AnnotationVisitor?, remap: AtomicBoolean, refmapBuilder: RefmapBuilderClassVisitor) : AnnotationVisitor(Constant.ASM_VERSION, parent) {

    override fun visit(name: String, value: Any) {
        TODO("Desc not supported yet.")
    }

    override fun visitAnnotation(name: String, descriptor: String?): AnnotationVisitor {
        TODO("Desc not supported yet.")
    }

}
