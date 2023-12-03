package xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.annotations.method

import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.HardTargetRemappingClassVisitor
import java.util.concurrent.atomic.AtomicBoolean


abstract class AbstractMethodAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor?,
    methodAccess: Int,
    protected val methodName: String,
    protected val methodDescriptor: String,
    protected val methodSignature: String?,
    methodExceptions: Array<out String>?,
    protected val hardTargetRemapper: HardTargetRemappingClassVisitor
) : AnnotationVisitor(Constant.ASM_VERSION, parent) {

    protected open val remap = AtomicBoolean(hardTargetRemapper.remap.get())
    protected val logger = hardTargetRemapper.logger
    protected val existingMappings = hardTargetRemapper.existingMappings
    protected val targetClasses = hardTargetRemapper.targetClasses
    protected val mixinName = hardTargetRemapper.mixinName


}