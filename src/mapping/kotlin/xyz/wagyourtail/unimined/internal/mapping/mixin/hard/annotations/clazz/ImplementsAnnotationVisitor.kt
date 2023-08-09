package xyz.wagyourtail.unimined.internal.mapping.mixin.hard.annotations.clazz

import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.*
import xyz.wagyourtail.unimined.internal.mapping.mixin.hard.HardTargetRemappingClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.ArrayVisitorWrapper

class ImplementsAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    private val hardTargetRemapper: HardTargetRemappingClassVisitor
) : AnnotationVisitor(Constant.ASM_VERSION, parent) {

    companion object {

        fun shouldVisit(descriptor: String, visible: Boolean, hardTargetRemapper: HardTargetRemappingClassVisitor): Boolean {
            return descriptor == Annotation.IMPLEMENTS
        }

    }

    override fun visitArray(name: String?): AnnotationVisitor {
        val delegate = super.visitArray(name)
        if (name == AnnotationElement.VALUE) {
            return ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { InterfaceAnnotationVisitor(it, hardTargetRemapper) }
        }
        return delegate
    }

    class InterfaceAnnotationVisitor(
        parent: AnnotationVisitor,
        private val hardTargetRemapper: HardTargetRemappingClassVisitor
    ) : AnnotationVisitor(Constant.ASM_VERSION, parent) {

        lateinit var iface: Type
        lateinit var prefix: String
        var remap: Remap = Remap.ALL

        override fun visit(name: String?, value: Any) {
            when (name) {
                AnnotationElement.IFACE -> {
                    iface = value as Type
                }
                AnnotationElement.PREFIX -> {
                    prefix = value as String
                }
            }
            super.visit(name, value)
        }

        override fun visitEnum(name: String?, descriptor: String, value: String) {
            super.visitEnum(name, descriptor, value)
            when (name) {
                AnnotationElement.REMAP -> {
                    remap = Remap.valueOf(value)
                }
            }
        }

        override fun visitEnd() {
            super.visitEnd()
            hardTargetRemapper.insertVisitor {
                object : ClassVisitor(Constant.ASM_VERSION, it) {
                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?
                    ): MethodVisitor {
                        hardTargetRemapper.addRemapTask {
                            val prefixed = name.startsWith(prefix)
                            if (remap == Remap.NONE || (remap == Remap.ONLY_PREFIXED && !prefixed)) return@addRemapTask
                            // TODO: force check
                            val fixedName = if (prefixed) name.substring(prefix.length) else name
                            // check to remap
                            val resolved = resolver.resolveMethod(iface.internalName, fixedName, descriptor, ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE)
                            resolved.ifPresent {
                                val mappedName = if (prefixed) "${prefix}${mapper.mapName(resolved.get())}" else mapper.mapName(resolved.get())
                                propagate(resolved.get(), mappedName)
                            }
                        }
                        return super.visitMethod(access, name, descriptor, signature, exceptions)
                    }
                }
            }
        }


    }

    enum class Remap {
        ALL, // match all, not just prefixed
        FORCE, // match prefixed, non-prefixed that match and are remapped will throw
        ONLY_PREFIXED, // match prefixed only
        NONE // match none
    }

}
