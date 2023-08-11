package xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard.annotations.method

import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.JarModAgent
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.HardTargetRemappingClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.annotations.method.AbstractMethodAnnotationVisitor

class COverrideAnnotationVisitor(
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
            return descriptor == JarModAgent.Annotation.COVERRIDE
        }

    }

    val names: MutableList<String> = mutableListOf()

    override fun visitArray(name: String): AnnotationVisitor {
        val delegate = super.visitArray(name)
        return when (name) {
            AnnotationElement.VALUE -> {
                object: AnnotationVisitor(Constant.ASM_VERSION, delegate) {
                    override fun visit(name: String?, value: Any) {
                        super.visit(name, value)
                        names.add(value as String)
                    }
                }
            }

            else -> {
                delegate
            }
        }
    }
    override fun visitEnd() {
        hardTargetRemapper.addRemapTask {
            if (remap.get() && names.isEmpty()) {
                for (target in targetClasses) {
                    val resolved = resolver.resolveMethod(target, methodName, methodDescriptor, ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE)
                    resolved.ifPresent {
                        val mappedName = mapper.mapName(resolved.get())
                        propagate(hardTargetRemapper.mxClass.getMethod(methodName, methodDescriptor).asTrMember(resolver), mappedName)
                    }
                    if (resolved.isPresent) {
                        return@addRemapTask
                    }
                }
                logger.warn("Could not find target method for @COverride $methodName$methodDescriptor in $mixinName")
            }
        }
        super.visitEnd()
    }

}