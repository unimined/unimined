package xyz.wagyourtail.unimined.internal.mapping.extension.jma.hard.annotations.clazz

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Type
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.JarModAgent
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.dontRemap
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.HardTargetRemappingClassVisitor
import xyz.wagyourtail.unimined.util.orElseOptional
import java.util.*

class CTransformerAnnotationVisitor(
    val descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor?,
    private val hardTargetRemapper: HardTargetRemappingClassVisitor
) : AnnotationVisitor(Constant.ASM_VERSION, parent) {


    companion object {

        fun shouldVisit(descriptor: String, visible: Boolean, hardTargetRemapper: HardTargetRemappingClassVisitor): Boolean {
            return descriptor == JarModAgent.Annotation.CTRANSFORMER
        }

    }

    private val remap = !hardTargetRemapper.dontRemap(descriptor)
    private val logger = hardTargetRemapper.logger
    private val existingMappings = hardTargetRemapper.existingMappings
    private val targetClasses = hardTargetRemapper.targetClasses
    private val mixinName = hardTargetRemapper.mixinName

    private val classTargets = mutableListOf<String>()
    private val classValues = mutableListOf<String>()

    override fun visit(name: String, value: Any) {
        super.visit(name, value)
        logger.info("Found annotation value $name: $value")
    }

    override fun visitArray(name: String?): AnnotationVisitor {
        return when (name) {
            JarModAgent.AnnotationElement.NAME -> {
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
        targetClasses.addAll((classValues + classTargets.map { it.replace('.', '/') }).toSet())
        hardTargetRemapper.addRemapTaskFirst {
            if (remap) {
                for (target in targetClasses) {
                    val clz = resolver.resolveClass(target.replace('.', '/')).orElseOptional {
                        existingMappings[target]?.let {
                            targetClasses.remove(target)
                            targetClasses.add(it)
                            resolver.resolveClass(it)
                        } ?: Optional.empty()
                    }
                    if (!clz.isPresent) {
                        logger.warn("Failed to resolve class $target in mixin ${mixinName.replace('/', '.')}")
                    }
                }
            }
        }
    }

}