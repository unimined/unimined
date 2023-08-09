package xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.annotations.method

import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.HardTargetRemappingClassVisitor

class OverwriteAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor?,
    methodAccess: Int,
    methodName: String,
    methodDescriptor: String,
    methodSignature: String?,
    methodExceptions: Array<out String>?,
    refmapBuilder: HardTargetRemappingClassVisitor,
)  : AbstractMethodAnnotationVisitor(
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

    companion object {

        fun shouldVisit(
            descriptor: String,
            visible: Boolean,
            methodAccess: Int,
            methodName: String,
            methodDescriptor: String,
            methodSignature: String?,
            methodExceptions: Array<out String>?,
            hardTargetRemapper: HardTargetRemappingClassVisitor
        ): Boolean {
            return descriptor == Annotation.OVERWRITE
        }

    }

    override fun visit(name: String?, value: Any?) {
        if (name == AnnotationElement.REMAP) {
            remap.set(value as Boolean)
        }
        super.visit(name, value)
    }

    override fun visitEnd() {
        hardTargetRemapper.addRemapTask {
            if (remap.get()) {
                for (target in targetClasses) {
                    val resolved = resolver.resolveMethod(target, methodName, methodDescriptor, ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE)
                    resolved.ifPresent {
                        propagate(hardTargetRemapper.mxClass.getMethod(methodName, methodDescriptor).asTrMember(resolver), mapper.mapName(it))
                    }
                    if (resolved.isPresent) {
                        return@addRemapTask
                    }
                }
                logger.warn("Could not find target method for @Overwrite $methodName$methodDescriptor in $mixinName")
            }
        }
        super.visitEnd()
    }

}