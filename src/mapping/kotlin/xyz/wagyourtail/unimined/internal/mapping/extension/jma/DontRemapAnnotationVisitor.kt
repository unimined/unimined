package xyz.wagyourtail.unimined.internal.mapping.extension.jma

import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.*
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.hard.HardTargetRemappingClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor


class DontRemapAnnotationVisitor(api: Int, parent: AnnotationVisitor?, val onEnd: (DontRemapAnnotationVisitor) -> Unit) :
    AnnotationVisitor(api, parent) {

    companion object {
        fun shouldVisitHardClass(
            descriptor: String,
            visible: Boolean,
            hardTargetRemapper: HardTargetRemappingClassVisitor
        ): Boolean {
            return descriptor == JarModAgent.Annotation.DONTREMAP
        }

        fun shouldVisitHardMethod(
            descriptor: String,
            visible: Boolean,
            methodAccess: Int,
            methodName: String,
            methodDescriptor: String,
            methodSignature: String?,
            methodExceptions: Array<out String>?,
            hardTargetRemapper: HardTargetRemappingClassVisitor
        ): Boolean {
            return descriptor == JarModAgent.Annotation.DONTREMAP
        }

        fun shouldVisitHardField(
            descriptor: String,
            visible: Boolean,
            access: Int,
            fieldName: String,
            fieldDescriptor: String,
            fieldSignature: String?,
            fieldValue: Any?,
            hardTargetRemapper: HardTargetRemappingClassVisitor
        ): Boolean {
            return descriptor == JarModAgent.Annotation.DONTREMAP
        }

        fun shouldVisitSoftClass(
            descriptor: String,
            visible: Boolean,
            hardTargetRemapper: RefmapBuilderClassVisitor
        ): Boolean {
            return descriptor == JarModAgent.Annotation.DONTREMAP
        }

        fun shouldVisitSoftMethod(
            descriptor: String,
            visible: Boolean,
            methodAccess: Int,
            methodName: String,
            methodDescriptor: String,
            methodSignature: String?,
            methodExceptions: Array<out String>?,
            hardTargetRemapper: RefmapBuilderClassVisitor
        ): Boolean {
            return descriptor == JarModAgent.Annotation.DONTREMAP
        }

        fun shouldVisitSoftField(
            descriptor: String,
            visible: Boolean,
            access: Int,
            fieldName: String,
            fieldDescriptor: String,
            fieldSignature: String?,
            fieldValue: Any?,
            hardTargetRemapper: RefmapBuilderClassVisitor
        ): Boolean {
            return descriptor == JarModAgent.Annotation.DONTREMAP
        }

        fun visitHardClass(
            descriptor: String,
            visible: Boolean,
            parent: AnnotationVisitor?,
            hardTargetRemapper: HardTargetRemappingClassVisitor
        ): AnnotationVisitor {
            return DontRemapAnnotationVisitor(Constant.ASM_VERSION, parent) {
                hardTargetRemapper.dontRemap = it
            }
        }

        fun visitHardMethod(
            descriptor: String,
            visible: Boolean,
            parent: AnnotationVisitor?,
            methodAccess: Int,
            methodName: String,
            methodDescriptor: String,
            methodSignature: String?,
            methodExceptions: Array<out String>?,
            hardTargetRemapper: HardTargetRemappingClassVisitor,
        ): AnnotationVisitor {
            return DontRemapAnnotationVisitor(Constant.ASM_VERSION, parent) {
                hardTargetRemapper.dontRemap = it
            }
        }

        fun visitHardField(
        descriptor: String,
        visible: Boolean,
        parent: AnnotationVisitor?,
        fieldAccess: Int,
        fieldName: String,
        fieldDescriptor: String,
        fieldSignature: String?,
        fieldValue: Any?,
        hardTargetRemapper: HardTargetRemappingClassVisitor
        ): AnnotationVisitor {
            return DontRemapAnnotationVisitor(Constant.ASM_VERSION, parent) {
                hardTargetRemapper.dontRemap = it
            }
        }

        fun visitSoftClass(
            descriptor: String,
            visible: Boolean,
            parent: AnnotationVisitor?,
            hardTargetRemapper: RefmapBuilderClassVisitor
        ): AnnotationVisitor {
            return DontRemapAnnotationVisitor(Constant.ASM_VERSION, parent) {
                hardTargetRemapper.dontRemap = it
            }
        }

        fun visitSoftMethod(
            descriptor: String,
            visible: Boolean,
            parent: AnnotationVisitor,
            methodAccess: Int,
            methodName: String,
            methodDescriptor: String,
            methodSignature: String?,
            methodExceptions: Array<out String>?,
            hardTargetRemapper: RefmapBuilderClassVisitor
        ): AnnotationVisitor {
            return DontRemapAnnotationVisitor(Constant.ASM_VERSION, parent) {
                hardTargetRemapper.dontRemap = it
            }
        }

        fun visitSoftField(
            descriptor: String,
            visible: Boolean,
            parent: AnnotationVisitor,
            fieldAccess: Int,
            fieldName: String,
            fieldDescriptor: String,
            fieldSignature: String?,
            fieldValue: Any?,
            hardTargetRemapper: RefmapBuilderClassVisitor
        ): AnnotationVisitor {
            return DontRemapAnnotationVisitor(Constant.ASM_VERSION, parent) {
                hardTargetRemapper.dontRemap = it
            }
        }


    }


    var dontRemap: MutableList<Type> = ArrayList()
    var skip = false

    override fun visit(name: String, value: Any) {
        if (name == "skip") {
            skip = value as Boolean
        }
        super.visit(name, value)
    }

    override fun visitArray(name: String?): AnnotationVisitor? {
        return if (name == "value" || name == null) {
            object : AnnotationVisitor(api, super.visitArray(name)) {
                override fun visit(name: String?, value: Any) {
                    dontRemap.add(value as Type)
                    super.visit(name, value)
                }
            }
        } else super.visitArray(name)
    }

    override fun visitEnd() {
        onEnd(this)
        super.visitEnd()
    }

    class DontRemapClassVisitor(api: Int, parent: ClassVisitor, val extraData: MutableMap<String, Any>) : ClassVisitor(api, parent) {

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (descriptor == JarModAgent.Annotation.DONTREMAP) return super.visitAnnotation(descriptor, visible)
            return object : AnnotationVisitor(api, super.visitAnnotation(descriptor, visible)) {

                override fun visitEnd() {
                    if ((extraData["dontRemap"] as DontRemapAnnotationVisitor?)?.skip != true) {
                        extraData.remove("dontRemap")
                    }
                    super.visitEnd()
                }
            }
        }

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            return object : MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                    if (descriptor == JarModAgent.Annotation.DONTREMAP) return super.visitAnnotation(descriptor, visible)
                    return object : AnnotationVisitor(api, super.visitAnnotation(descriptor, visible)) {

                        override fun visitEnd() {
                            if ((extraData["dontRemap"] as DontRemapAnnotationVisitor?)?.skip != true) {
                                extraData.remove("dontRemap")
                            }
                            super.visitEnd()
                        }
                    }
                }

                override fun visitEnd() {
                    if ((extraData["dontRemap"] as DontRemapAnnotationVisitor?)?.skip != true) {
                        extraData.remove("dontRemap")
                    }
                    super.visitEnd()
                }
            }
        }

        override fun visitField(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            value: Any?
        ): FieldVisitor {

            return object : FieldVisitor(api, super.visitField(access, name, descriptor, signature, value)) {
                override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                    if (descriptor == JarModAgent.Annotation.DONTREMAP) return super.visitAnnotation(descriptor, visible)
                    return object : AnnotationVisitor(api, super.visitAnnotation(descriptor, visible)) {

                        override fun visitEnd() {
                            if ((extraData["dontRemap"] as DontRemapAnnotationVisitor?)?.skip != true) {
                                extraData.remove("dontRemap")
                            }
                            super.visitEnd()
                        }
                    }
                }

                override fun visitEnd() {
                    if ((extraData["dontRemap"] as DontRemapAnnotationVisitor?)?.skip != true) {
                        extraData.remove("dontRemap")
                    }
                    super.visitEnd()
                }
            }

        }

    }
}

var HardTargetRemappingClassVisitor.dontRemap: DontRemapAnnotationVisitor?
    get() = this.extraData["dontRemap"] as DontRemapAnnotationVisitor?
    set(value) {
        if (value == null) throw NullPointerException()
        else this.extraData["dontRemap"] = value
    }

var RefmapBuilderClassVisitor.dontRemap: DontRemapAnnotationVisitor?
    get() = this.extraData["dontRemap"] as DontRemapAnnotationVisitor?
    set(value) {
        if (value == null) throw NullPointerException()
        else this.extraData["dontRemap"] = value
    }

fun HardTargetRemappingClassVisitor.dontRemap(annDesc: String): Boolean {
    if (dontRemap == null) return false
    return dontRemap!!.skip || dontRemap!!.dontRemap.contains(Type.getType(annDesc))
}

fun RefmapBuilderClassVisitor.dontRemap(annDesc: String): Boolean {
    if (dontRemap == null) return false
    return dontRemap!!.skip || dontRemap!!.dontRemap.contains(Type.getType(annDesc))
}