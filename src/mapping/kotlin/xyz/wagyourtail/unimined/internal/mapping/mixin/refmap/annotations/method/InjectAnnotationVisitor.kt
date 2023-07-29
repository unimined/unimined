package xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.annotations.method

import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.ArrayVisitorWrapper
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.RefmapBuilderClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.annotations.AtAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.annotations.DescAnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.mixin.refmap.annotations.SliceAnnotationVisitor

@Suppress("UNUSED_PARAMETER")
class InjectAnnotationVisitor(
    descriptor: String,
    visible: Boolean,
    parent: AnnotationVisitor,
    methodAccess: Int,
    methodName: String,
    methodDescriptor: String,
    methodSignature: String?,
    methodExceptions: Array<out String>?,
    refmapBuilder: RefmapBuilderClassVisitor,
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

    override val annotationName: String = "@Inject"

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
            return descriptor == Annotation.INJECT
        }
    }

    override fun visitArray(name: String): AnnotationVisitor {
        val delegate = super.visitArray(name)
        return when (name) {
            AnnotationElement.AT -> {
                ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { AtAnnotationVisitor(it, remap, refmapBuilder) }
            }

            AnnotationElement.SLICE -> {
                ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { SliceAnnotationVisitor(it, remap, refmapBuilder) }
            }

            AnnotationElement.TARGET -> {
                ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { DescAnnotationVisitor(it, remap, refmapBuilder) }
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

    private val callbackInfo = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo"
    private val callbackInfoReturn = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable"

    private fun parseCIRVal(): String? {
        val remain = methodSignature?.substringAfterLast("$callbackInfoReturn<", "") ?: ""
        if (remain.isEmpty()) {
            return null
        }
        var valBuild = ""
        var depth = 1
        for (c in remain) {
            if (c == '<') {
                depth++
            } else if (c == '>') {
                depth--
            }
            if (depth == 0) {
                break
            }
            valBuild += c
        }
        return valBuild.substringBefore("<").substringBefore(";") + ";"
    }

    private fun toPrimitive(sig: String): String? {
        return when (sig) {
            "Ljava/lang/Integer;" -> "I"
            "Ljava/lang/Long;" -> "J"
            "Ljava/lang/Short;" -> "S"
            "Ljava/lang/Byte;" -> "B"
            "Ljava/lang/Character;" -> "C"
            "Ljava/lang/Float;" -> "F"
            "Ljava/lang/Double;" -> "D"
            "Ljava/lang/Boolean;" -> "Z"
            else -> null
        }
    }

    private fun stripCallbackInfoFromDesc(): Set<String?> {
        val desc = methodDescriptor.substringBeforeLast(callbackInfo) + ")V"
        if (methodDescriptor.contains(callbackInfoReturn)) {
            val returnType = parseCIRVal()
            if (returnType == null) {
                logger.warn("Failed to parse return type from ($methodName$methodDescriptor) $methodSignature on $mixinName")
                return setOf(null)
            }
            val rets = setOfNotNull(
                desc.replace(")V", ")$returnType"),
                toPrimitive(returnType)?.let { desc.replace(")V", ")${it}") })
            logger.info("Found returnable inject, signatures $rets, return type $returnType")
            return rets
        }
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
