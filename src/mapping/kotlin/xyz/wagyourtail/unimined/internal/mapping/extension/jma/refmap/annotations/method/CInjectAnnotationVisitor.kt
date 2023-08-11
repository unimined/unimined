package xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.method

import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.ArrayVisitorWrapper
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.JarModAgent
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.CSliceAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.jma.refmap.annotations.CTargetAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor

@Suppress("UNUSED_PARAMETER")
class CInjectAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    methodAccess: Int,
    methodName: String,
    methodDescriptor: String,
    methodSignature: String?,
    methodExceptions: Array<out String>?,
    refmapBuilder: RefmapBuilderClassVisitor,
)  : CAbstractMethodAnnotationVisitor(
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

    override val annotationName: String = "@CInject"

    companion object{
        fun shouldVisit(
            descriptor:String,
            visible:Boolean,
            methodAccess:Int,
            methodName:String,
            methodDescriptor:String,
            methodSignature:String?,
            methodExceptions:Array<out String>?,
            refmapBuilder:RefmapBuilderClassVisitor
        ): Boolean{
            return descriptor == JarModAgent.Annotation.CINJECT
        }
    }

    override fun visitArray(name: String): AnnotationVisitor {
        val delegate = super.visitArray(name)
        return when (name) {
            AnnotationElement.TARGET -> {
                ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { CTargetAnnotationVisitor(it, remap, refmapBuilder) }
            }

            AnnotationElement.SLICE -> {
                ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { CSliceAnnotationVisitor(it, remap, refmapBuilder) }
            }
            AnnotationElement.METHOD -> {
                object: AnnotationVisitor(Constant.ASM_VERSION, delegate) {
                    override fun visit(name: String?, value: Any) {
                        super.visit(name, value)
                        targetNames.add(value as String)
                    }
                }
            }

            else -> {
                delegate
            }
        }
    }

    private val callbackInfo = "Lnet/lenni0451/classtransform/InjectionCallback"

    private fun stripCallbackInfoFromDesc(): Set<String?> {
        val desc = methodDescriptor.substringBeforeLast(callbackInfo) + ")"
        return setOf(desc)
    }

    override fun getTargetNameAndDescs(targetMethod: String, wildcard: Boolean): Pair<String, Set<String?>> {
        return if (targetMethod.contains("(")) {
            val n = targetMethod.split("(")
            (n[0] to setOf("(${n[1]}"))
        } else {
            if (wildcard) {
                (targetMethod.substring(0, targetMethod.length - 1) to setOf(null))
            } else {
                (targetMethod to stripCallbackInfoFromDesc() + setOf(null))
            }
        }
    }

}
