package xyz.wagyourtail.unimined.internal.mapping.mixin.refmap

import com.google.gson.JsonObject
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.annotations.clazz.MixinAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.annotations.method.*
import java.util.concurrent.atomic.AtomicBoolean


typealias ClassAnnotationPredicate = (
        descriptor: String,
        visible: Boolean,
        refmapBuilder: RefmapBuilderClassVisitor
    ) -> Boolean
typealias ClassAnnotationVisitor = (
        descriptor: String,
        visible: Boolean,
        parent: AnnotationVisitor,
        refmapBuilder: RefmapBuilderClassVisitor
    ) -> AnnotationVisitor

typealias MethodAnnotationPredicate = (
        descriptor: String,
        visible: Boolean,
        methodAccess: Int,
        methodName: String,
        methodDescriptor: String,
        methodSignature: String?,
        methodExceptions: Array<out String>?,
        refmapBuilder: RefmapBuilderClassVisitor
    ) -> Boolean
typealias MethodAnnotationVisitor = (
        descriptor: String,
        visible: Boolean,
        parent: AnnotationVisitor,
        methodAccess: Int,
        methodName: String,
        methodDescriptor: String,
        methodSignature: String?,
        methodExceptions: Array<out String>?,
        refmapBuilder: RefmapBuilderClassVisitor
    ) -> AnnotationVisitor

typealias FieldAnnotationPredicate = (
        descriptor: String,
        visible: Boolean,
        access: Int,
        fieldName: String,
        fieldDescriptor: String,
        fieldSignature: String?,
        fieldValue: Any?,
        refmapBuilder: RefmapBuilderClassVisitor
    ) -> Boolean
typealias FieldAnnotationVisitor = (
        descriptor: String,
        visible: Boolean,
        parent: AnnotationVisitor,
        fieldAccess: Int,
        fieldName: String,
        fieldDescriptor: String,
        fieldSignature: String?,
        fieldValue: Any?,
        refmapBuilder: RefmapBuilderClassVisitor
    ) -> AnnotationVisitor

class RefmapBuilderClassVisitor(
    commonData: CommonData,
    val mixinName: String,
    val refmap: JsonObject,
    delegate: ClassVisitor,
    val existingMappings: Map<String, String>,
    private val onEnd: () -> Unit = {},
    val allowImplicitWildcards: Boolean = false
) : ClassVisitor(Constant.ASM_VERSION, delegate) {

    val mapper = commonData.mapper
    val resolver = commonData.resolver
    val logger = commonData.logger

    val classAnnotationVisitors: MutableList<Pair<ClassAnnotationPredicate, ClassAnnotationVisitor>> = mutableListOf(
        MixinAnnotationVisitor.Companion::shouldVisit to ::MixinAnnotationVisitor
    )

    val fieldAnnotationVisitors: MutableList<Pair<FieldAnnotationPredicate, FieldAnnotationVisitor>> = mutableListOf()

    val methodAnnotationVisitors: MutableList<Pair<MethodAnnotationPredicate, MethodAnnotationVisitor>> = mutableListOf(
        AccessorAnnotationVisitor.Companion::shouldVisit to ::AccessorAnnotationVisitor,
        InvokerAnnotationVisitor.Companion::shouldVisit to ::InvokerAnnotationVisitor,
        InjectAnnotationVisitor.Companion::shouldVisit to ::InjectAnnotationVisitor,
        ModifyArgAnnotationVisitor.Companion::shouldVisit to ::ModifyArgAnnotationVisitor,
        ModifyArgsAnnotationVisitor.Companion::shouldVisit to ::ModifyArgsAnnotationVisitor,
        ModifyConstantAnnotationVisitor.Companion::shouldVisit to ::ModifyConstantAnnotationVisitor,
        ModifyVariableAnnotationVisitor.Companion::shouldVisit to ::ModifyVariableAnnotationVisitor,
        RedirectAnnotationVisitor.Companion::shouldVisit to ::RedirectAnnotationVisitor,
    )

    val targetClasses = mutableSetOf<String>()

    val remap = AtomicBoolean(true)

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
        if (descriptor != null) {
            for ((predicate, visitor) in classAnnotationVisitors) {
                if (predicate(descriptor, visible, this)) {
                    return visitor(descriptor, visible, super.visitAnnotation(descriptor, visible), this)
                }
            }
        }
        return super.visitAnnotation(descriptor, visible)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        return object : MethodVisitor(Constant.ASM_VERSION, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
                if (descriptor != null) {
                    for ((predicate, visitor) in methodAnnotationVisitors) {
                        if (predicate(descriptor, visible, access, name, descriptor, signature, exceptions, this@RefmapBuilderClassVisitor)) {
                            return visitor(descriptor, visible, super.visitAnnotation(descriptor, visible), access, name, descriptor, signature, exceptions, this@RefmapBuilderClassVisitor)
                        }
                    }
                }
                return super.visitAnnotation(descriptor, visible)
            }
        }
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor {
        return object : FieldVisitor(Constant.ASM_VERSION, super.visitField(access, name, descriptor, signature, value)) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
                if (descriptor != null) {
                    for ((predicate, visitor) in fieldAnnotationVisitors) {
                        if (predicate(descriptor, visible, access, name, descriptor, signature, value, this@RefmapBuilderClassVisitor)) {
                            return visitor(descriptor, visible, super.visitAnnotation(descriptor, visible), access, name, descriptor, signature, value, this@RefmapBuilderClassVisitor)
                        }
                    }
                }
                return super.visitAnnotation(descriptor, visible)
            }
        }
    }

    override fun visitEnd() {
        super.visitEnd()
        onEnd()
    }


}
