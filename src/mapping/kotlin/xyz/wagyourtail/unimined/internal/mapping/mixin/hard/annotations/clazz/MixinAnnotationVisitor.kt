package xyz.wagyourtail.unimined.internal.mapping.mixin.hard.annotations.clazz

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Type
import xyz.wagyourtail.unimined.internal.mapping.mixin.hard.HardTargetRemappingClassVisitor

class MixinAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    hardTargetRemapper: HardTargetRemappingClassVisitor
) : AnnotationVisitor(Constant.ASM_VERSION, parent) {

    private val remap = hardTargetRemapper.remap
    private val logger = hardTargetRemapper.logger
    private val existingMappings = hardTargetRemapper.existingMappings
    private val targetClasses = hardTargetRemapper.targetClasses

    private val classTargets = mutableListOf<String>()
    private val classValues = mutableListOf<String>()

    override fun visit(name: String, value: Any) {
        super.visit(name, value)
        logger.info("Found annotation value $name: $value")
        if (name == AnnotationElement.REMAP) {
            remap.set(value as Boolean)
        }
    }

    override fun visitArray(name: String?): AnnotationVisitor {
        return when (name) {
            AnnotationElement.TARGETS -> {
                return object: AnnotationVisitor(Constant.ASM_VERSION, super.visitArray(name)) {
                    override fun visit(name: String?, value: Any) {
                        classTargets.add(value as String)
                        super.visit(name, value)
                    }
                }
            }

            AnnotationElement.VALUE, null -> {
                return object: AnnotationVisitor(Constant.ASM_VERSION, super.visitArray(name)) {
                    override fun visit(name: String?, value: Any) {
                        classValues.add((value as Type).internalName)
                        super.visit(name, value)
                    }
                }
            }

            else -> {
                super.visitArray(name)
            }
        }
    }

    override fun visitEnd() {
        super.visitEnd()
        if (remap.get()) {
            for (target in classTargets.toSet()) {
                existingMappings[target]?.let {
                    classTargets.remove(target)
                    classTargets.add(it)
                }
            }
            for (target in classValues.toSet()) {
                existingMappings[target]?.let {
                    classValues.remove(target)
                    classValues.add(it)
                }
            }
        }
        targetClasses.addAll((classValues + classTargets.map { it.replace('.', '/') }).toSet())
    }

}