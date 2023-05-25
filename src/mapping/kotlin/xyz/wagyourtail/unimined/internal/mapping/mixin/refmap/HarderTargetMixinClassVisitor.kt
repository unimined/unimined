package xyz.wagyourtail.unimined.internal.mapping.mixin.refmap

import net.fabricmc.tinyremapper.extension.mixin.common.Logger
import net.fabricmc.tinyremapper.extension.mixin.common.MapUtility
import net.fabricmc.tinyremapper.extension.mixin.common.data.*
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.ImplementsAnnotationVisitor
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.OverwriteAnnotationVisitor
import net.fabricmc.tinyremapper.extension.mixin.hard.annotation.ShadowAnnotationVisitor
import net.fabricmc.tinyremapper.extension.mixin.hard.data.SoftInterface
import org.objectweb.asm.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer


class HarderTargetMixinClassVisitor(
    private val tasks: MutableList<Consumer<CommonData>>,
    delegate: ClassVisitor?,
    private val existingMappings: Map<String, String>,
    private val logger: Logger
):
        ClassVisitor(Constant.ASM_VERSION, delegate) {
    private var _class: MxClass? = null

    // @Mixin
    private val remap = AtomicBoolean()
    private val targets: MutableList<String> = mutableListOf()

    // @Implements
    private val interfaces: MutableList<SoftInterface> = mutableListOf()


    /**
     * This is called before visitAnnotation.
     */
    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String,
        interfaces: Array<String>
    ) {
        _class = MxClass(name)
        super.visit(version, access, name, signature, superName, interfaces)
    }

    /**
     * This is called before visitMethod & visitField.
     */
    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        var av = super.visitAnnotation(descriptor, visible)
        if (Annotation.MIXIN == descriptor) {
            av = MixinAnnotationVisitor(av, remap, targets, existingMappings, logger)
            for (target in targets.toSet()) {
                existingMappings[target]?.let {
                    targets.remove(target)
                    targets.add(it)
                }
                existingMappings[target.replace('/', '.')]?.let {
                    targets.remove(target)
                    targets.add(it.replace('.', '/'))
                }
            }
        } else if (Annotation.IMPLEMENTS == descriptor) {
            av = ImplementsAnnotationVisitor(av, interfaces)
        }
        return av
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor? {
        val fv = super.visitField(access, name, descriptor, signature, value)
        val field = _class!!.getField(name, descriptor)
        return if (targets.isEmpty()) {
            fv
        } else {
            HardTargetMixinFieldVisitor(tasks, fv, field, remap.get(), Collections.unmodifiableList(targets), logger)
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        val method = _class!!.getMethod(name, descriptor)
        if (interfaces.isNotEmpty() && !MapUtility.IGNORED_NAME.contains(name)) {
            ImplementsAnnotationVisitor.visitMethod(tasks, method, interfaces)
        }
        return if (targets.isEmpty()) {
            mv
        } else {
            HarderTargetMixinMethodVisitor(tasks, mv, method, remap.get(), Collections.unmodifiableList(targets), logger)
        }
    }


    internal class HarderTargetMixinMethodVisitor(
        private val data: MutableList<Consumer<CommonData>>,
        delegate: MethodVisitor?,
        private val method: MxMember,
        private val remap: Boolean,
        private val targets: List<String>,
        private val logger: Logger
    ):
            MethodVisitor(Constant.ASM_VERSION, delegate) {

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            var av = super.visitAnnotation(descriptor, visible)
            if (Annotation.SHADOW == descriptor) {
                logger.info("Found shadow annotation on method ${method.name}")
                av = ShadowAnnotationVisitor(data, av, method, remap, targets)
            } else if (Annotation.OVERWRITE == descriptor) {
                logger.info("Found overwrite annotation on method ${method.name}")
                av = OverwriteAnnotationVisitor(data, av, method, remap, targets)
            }
            return av
        }
    }

    internal class HardTargetMixinFieldVisitor(
        private val tasks: MutableList<Consumer<CommonData>>, delegate: FieldVisitor?, private val field: MxMember,
        private val remap: Boolean, private val targets: List<String>,
        private val logger: Logger
    ): FieldVisitor(Constant.ASM_VERSION, delegate) {


        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            var av = super.visitAnnotation(descriptor, visible)
            if (Annotation.SHADOW == descriptor) {
                logger.info("Found shadow annotation on field ${field.name}")
                av = ShadowAnnotationVisitor(tasks, av, field, remap, targets)
            }
            return av
        }
    }

    class MixinAnnotationVisitor(
        delegate: AnnotationVisitor?,
        remapOut: AtomicBoolean,
        targetsOut: MutableList<String>,
        val existingMappings: Map<String, String>,
        private val logger: Logger
    ):
            AnnotationVisitor(Constant.ASM_VERSION, delegate) {
        private val remap0: AtomicBoolean
        private val targets: MutableList<String>

        init {
            remap0 = Objects.requireNonNull(remapOut)
            targets = Objects.requireNonNull(targetsOut)
            remap0.set(true) // default value is true.
        }

        override fun visit(name: String?, value: Any) {
            if (name == AnnotationElement.REMAP) {
                remap0.set(Objects.requireNonNull(value as Boolean))
            }
            super.visit(name, value)
        }

        override fun visitArray(name: String?): AnnotationVisitor {
            val visitor = super.visitArray(name)
            return if (name == AnnotationElement.TARGETS) {
                object: AnnotationVisitor(Constant.ASM_VERSION, visitor) {
                    override fun visit(name: String?, value: Any) {
                        var value = (value as String)
                        val srcName = existingMappings[value] ?: value.replace("\\s".toRegex(), "").replace('.', '/')
                        logger.info("Found mixin annotation target $srcName")
                        targets.add(srcName)
                        super.visit(name, value)
                    }
                }
            } else if (name == AnnotationElement.VALUE || name == null) {
                object: AnnotationVisitor(Constant.ASM_VERSION, visitor) {
                    override fun visit(name: String?, value: Any) {
                        val srcType = Objects.requireNonNull(value as Type)
                        targets.add(srcType.internalName)
                        logger.info("Found mixin annotation target $srcType")
                        super.visit(name, value)
                    }
                }
            } else {
                visitor
            }
        }
    }
}