package xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.annotations.field

import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.HardTargetRemappingClassVisitor
import java.util.concurrent.atomic.AtomicBoolean

class ShadowFieldAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor?,
    fieldAccess: Int,
    protected val fieldName: String,
    protected val fieldDescriptor: String,
    fieldSignature: String?,
    fieldValue: Any?,
    protected val hardTargetRemapper: HardTargetRemappingClassVisitor,
)  : AnnotationVisitor(
    Constant.ASM_VERSION,
    parent
) {

    protected val remap = AtomicBoolean(hardTargetRemapper.remap.get())
    protected val logger = hardTargetRemapper.logger
    protected val existingMappings = hardTargetRemapper.existingMappings
    protected val targetClasses = hardTargetRemapper.targetClasses
    protected val mixinName = hardTargetRemapper.mixinName

    companion object {

        fun shouldVisit(
            descriptor: String,
            visible: Boolean,
            access: Int,
            fieldName: String,
            fieldDescriptor: String,
            fieldSignature: String?,
            fieldValue: Any?,
            hardTargetRemapper: HardTargetRemappingClassVisitor
        ): Boolean {
            return descriptor == Annotation.SHADOW
        }

    }

    var prefix: String = "shadow$"

    override fun visit(name: String?, value: Any?) {
        if (name == AnnotationElement.REMAP) {
            remap.set(value as Boolean)
        }
        if (name == AnnotationElement.PREFIX) {
            prefix = value as String
        }
        super.visit(name, value)
    }

    override fun visitEnd() {
        hardTargetRemapper.addRemapTask {
            if (remap.get()) {
                val prefixed = fieldName.startsWith(prefix)
                val fixedName = if (prefixed) fieldName.substring(prefix.length) else fieldName
                for (target in targetClasses) {
                    val resolved = resolver.resolveField(target, fixedName, fieldDescriptor, ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE)
                    resolved.ifPresent {
                        val mappedName = if (prefixed) "${prefix}${mapper.mapName(resolved.get())}" else mapper.mapName(resolved.get())
                        propagate(hardTargetRemapper.mxClass.getField(fieldName, fieldDescriptor).asTrMember(resolver), mappedName)
                    }
                    if (resolved.isPresent) {
                        return@addRemapTask
                    }
                }
                logger.warn("Could not find target field for @Shadow $fieldName:$fieldDescriptor in $mixinName")
            }
        }
        super.visitEnd()
    }

}