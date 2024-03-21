package xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.annotations.method

import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import org.objectweb.asm.AnnotationVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor
import xyz.wagyourtail.unimined.util.decapitalized
import xyz.wagyourtail.unimined.util.orElseOptional
import java.util.*

@Suppress("UNUSED_PARAMETER")
class InvokerAnnotationVisitor(
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

    override val annotationName: String = "@Invoker"

    companion object {
        fun shouldVisit(
            descriptor: String,
            visible: Boolean,
            methodAccess: Int,
            methodName: String,
            methodDescriptor: String,
            methodSignature: String?,
            methodExceptions: Array<out String>?,
            refmapBuilder: RefmapBuilderClassVisitor
        ): Boolean {
            return descriptor == Annotation.INVOKER
        }

        val validPrefixes = mutableSetOf(
            "call",
            "invoke",
            "new",
            "create"
        )

    }

    override fun visit(name: String?, value: Any) {
        if (!noRefmap) {
            super.visit(name, value)
        }
        if (name == AnnotationElement.VALUE || name == null) targetNames.add(value as String)
    }

    override fun visitEnd() {
        remapTargetNames {
            if (noRefmap) {
                super.visit(AnnotationElement.VALUE, it)
            }
        }
        super.visitEnd()
    }

    override fun remapTargetNames(noRefmapAcceptor: (String) -> Unit) {
        if (remap.get()) {
            val targetNames = if (targetNames.isEmpty()) {
                val prefix = validPrefixes.firstOrNull { methodName.startsWith(it) }
                if (prefix == null) {
                    logger.warn(
                        "Failed to resolve invoker $methodName in mixin ${
                            mixinName.replace(
                                '/',
                                '.'
                            )
                        }, unknown prefix"
                    )
                    return
                }
                listOf(methodName.substring(prefix.length).decapitalized(), methodName.substring(prefix.length))
            } else {
                targetNames
            }
            for (targetClass in targetClasses) {
                for (targetName in targetNames) {
                    var implicitWildcard = false
                    val target = resolver.resolveMethod(
                        targetClass,
                        targetName,
                        if (targetName == "<init>") "${methodDescriptor.substringBefore(")")})V" else methodDescriptor,
                        ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE
                    ).orElseOptional {
                        existingMappings[targetName]?.let {
                            logger.info("remapping $it from existing refmap")
                            val mName = it.substringBefore("(")
                            val desc = if (it.contains("(")) {
                                "(${it.substringAfter("(")}"
                            } else null
                            if (desc == null && allowImplicitWildcards) {
                                implicitWildcard = true
                            }
                            resolver.resolveMethod(
                                targetClass,
                                mName,
                                desc,
                                (if (implicitWildcard) ResolveUtility.FLAG_FIRST else ResolveUtility.FLAG_UNIQUE) or ResolveUtility.FLAG_RECURSIVE
                            )
                        } ?: Optional.empty()
                    }
                    target.ifPresent { targetVal ->
                        val mappedName = mapper.mapName(targetVal)
                        val mappedDesc = mapper.mapDesc(targetVal).let { if (mappedName == "<init>") "" else it }
//                                if (implicitWildcard) {
//                                    refmap.addProperty(targetName, mappedName)
//                                } else {
                        refmap.addProperty(targetName, "$mappedName$mappedDesc")
                        noRefmapAcceptor(mappedName)
//                                }
                    }
                    if (target.isPresent) return
                }
            }
            logger.warn(
                "Failed to resolve method invoker $targetNames ($methodName$methodDescriptor) in mixin ${
                    mixinName.replace(
                        '/',
                        '.'
                    )
                } targetClasses: $targetClasses"
            )
            noRefmapAcceptor(targetNames.first())
        } else {
            if (targetNames.isNotEmpty()) {
                noRefmapAcceptor(targetNames.first())
            }
        }
    }
}