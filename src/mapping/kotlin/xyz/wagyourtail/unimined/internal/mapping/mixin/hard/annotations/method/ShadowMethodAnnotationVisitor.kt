package xyz.wagyourtail.unimined.internal.mapping.mixin.hard.annotations.method

import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixin.hard.HardTargetRemappingClassVisitor

class ShadowMethodAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
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
            hartTargetRemapper: HardTargetRemappingClassVisitor
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
                val prefixed = methodName.startsWith(prefix)
                val fixedName = if (prefixed) methodName.substring(prefix.length) else methodName
                for (target in targetClasses) {
                    val resolved = resolver.resolveMethod(target, fixedName, methodDescriptor, ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE)
                    resolved.ifPresent {
                        val mappedName = if (prefixed) "${prefix}${mapper.mapName(resolved.get())}" else mapper.mapName(resolved.get())
                        propagate(resolved.get(), mappedName)
                    }
                    if (resolved.isPresent) {
                        return@addRemapTask
                    }
                }
                logger.warn("Could not find target method for @Shadow $methodName$methodDescriptor in $mixinName")
            }
        }
        super.visitEnd()
    }

}