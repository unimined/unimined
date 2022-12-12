package xyz.wagyourtail.unimined.refmap

import com.google.gson.JsonObject
import net.fabricmc.tinyremapper.extension.mixin.common.ResolveUtility
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation
import net.fabricmc.tinyremapper.extension.mixin.common.data.AnnotationElement
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData
import net.fabricmc.tinyremapper.extension.mixin.common.data.Constant
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type
import java.util.concurrent.atomic.AtomicBoolean

class MixinClassVisitorRefmapBuilder(
    commonData: CommonData,
    val mixinName: String,
    val refmap: JsonObject,
    delegate: ClassVisitor,
    private val onEnd: () -> Unit = {}
) : ClassVisitor(Constant.ASM_VERSION, delegate) {
    private val mapper = commonData.mapper
    private val resolver = commonData.resolver
    private val logger = commonData.logger
    private var remap = AtomicBoolean(true)

    val classTargets = mutableListOf<String>()
    val classValues = mutableListOf<String>()

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
        val av = if (Annotation.MIXIN == descriptor) {
            object : AnnotationVisitor(Constant.ASM_VERSION, super.visitAnnotation(descriptor, visible)) {
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
                            return object : AnnotationVisitor(Constant.ASM_VERSION, super.visitArray(name)) {
                                override fun visit(name: String?, value: Any) {
                                    if (remap.get()) {
                                        classTargets.add(value as String)
                                    }
                                    super.visit(name, value)
                                }
                            }
                        }

                        AnnotationElement.VALUE, null -> {
                            return object : AnnotationVisitor(Constant.ASM_VERSION, super.visitArray(name)) {
                                override fun visit(name: String?, value: Any) {
                                    if (remap.get()) {
                                        classValues.add((value as Type).internalName)
                                    }
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
                        for (target in classTargets) {
                            val clz = resolver.resolveClass(target.replace('.', '/'))
                            clz.ifPresent {
                                refmap.addProperty(target, mapper.mapName(it))
                            }
                            if (!clz.isPresent) {
                                logger.warn("Failed to resolve class $target in mixin ${mixinName.replace('/', '.')}")
                            }
                        }
                    }
                }
            }
        } else {
            super.visitAnnotation(descriptor, visible)
        }
        return av
    }

    override fun visitMethod(
        access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?
    ): MethodVisitor {
        val remap = AtomicBoolean(remap.get())
        return object : MethodVisitor(
            Constant.ASM_VERSION, super.visitMethod(access, name, descriptor, signature, exceptions)
        ) {

            val softTargets = mapOf<String, (AnnotationVisitor) -> AnnotationVisitor>(
                Annotation.ACCESSOR to ::visitAccessor,
                Annotation.INVOKER to ::visitInvoker,
                Annotation.INJECT to ::visitInject,
                Annotation.MODIFY_ARG to ::visitModifyArg,
                Annotation.MODIFY_ARGS to ::visitModifyArgs,
                Annotation.MODIFY_CONSTANT to ::visitModifyConstant,
                Annotation.MODIFY_VARIABLE to ::visitModifyVariable,
                Annotation.REDIRECT to ::visitRedirect,
            )

            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
                return softTargets[descriptor]?.invoke(super.visitAnnotation(descriptor, visible))
                    ?: super.visitAnnotation(descriptor, visible)
            }

            fun visitAccessor(visitor: AnnotationVisitor) = object : AnnotationVisitor(Constant.ASM_VERSION, visitor) {
                val remapAccessor = AtomicBoolean(remap.get())
                var targetName: String? = null
                override fun visit(name: String?, value: Any) {
                    super.visit(name, value)
                    if (name == AnnotationElement.VALUE || name == null) targetName = value as String
                    if (name == AnnotationElement.REMAP) remapAccessor.set(value as Boolean)
                }

                override fun visitEnd() {
                    super.visitEnd()
                    if (remapAccessor.get()) {
                        for (targetClass in classValues + classTargets.map { it.replace('.', '/') }) {
                            val targetName = if (targetName != null) {
                                targetName!!.split(":")[0]
                            } else {
                                val prefix = if (name.startsWith("get")) {
                                    "get"
                                } else if (name.startsWith("is")) {
                                    "is"
                                } else if (name.startsWith("set")) {
                                    "set"
                                } else {
                                    logger.warn(
                                        "Failed to resolve accessor $name in mixin ${
                                            mixinName.replace(
                                                '/',
                                                '.'
                                            )
                                        }, unknown prefix"
                                    )
                                    return
                                }
                                "${
                                    name.substring(prefix.length, prefix.length + 1)
                                        .lowercase()
                                }${name.substring(prefix.length + 1)}"
                            }
                            val targetDesc = if (descriptor.startsWith("()")) {
                                descriptor.split(')')[1]
                            } else {
                                descriptor.split(")")[0].substring(1)
                            }
                            val target = resolver.resolveField(
                                targetClass,
                                targetName,
                                targetDesc,
                                ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE
                            )
                            target.ifPresent {
                                val mappedName = mapper.mapName(it)
                                val mappedDesc = mapper.mapDesc(it)
                                refmap.addProperty(targetName, "$mappedName:$mappedDesc")
                            }
                            if (target.isPresent) return
                        }
                        logger.warn(
                            "Failed to resolve field accessor $targetName ($name$descriptor) in mixin ${
                                mixinName.replace(
                                    '/',
                                    '.'
                                )
                            }"
                        )
                    }
                }
            }

            fun visitInvoker(visitor: AnnotationVisitor) = object : AnnotationVisitor(Constant.ASM_VERSION, visitor) {
                val remapInvoker = AtomicBoolean(remap.get())
                var targetName: String? = null
                override fun visit(name: String?, value: Any) {
                    super.visit(name, value)
                    if (name == AnnotationElement.VALUE || name == null) targetName = value as String
                    if (name == AnnotationElement.REMAP) remapInvoker.set(value as Boolean)
                }

                override fun visitEnd() {
                    super.visitEnd()
                    if (remapInvoker.get()) {
                        for (targetClass in classValues + classTargets.map { it.replace('.', '/') }) {
                            val targetName = if (targetName != null) {
                                targetName
                            } else {
                                val prefix = if (name.startsWith("call")) {
                                    "call"
                                } else if (name.startsWith("invoke")) {
                                    "invoke"
                                } else if (name.startsWith("new")) {
                                    "new"
                                } else if (name.startsWith("create")) {
                                    "create"
                                } else {
                                    logger.warn(
                                        "Failed to resolve invoker $name in mixin ${
                                            mixinName.replace(
                                                '/',
                                                '.'
                                            )
                                        }, unknown prefix"
                                    )
                                    return
                                }
                                "${
                                    name.substring(prefix.length, prefix.length + 1)
                                        .lowercase()
                                }${name.substring(prefix.length + 1)}"
                            }
                            val target = resolver.resolveMethod(
                                targetClass,
                                targetName,
                                descriptor,
                                ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE
                            )
                            target.ifPresent {
                                val mappedName = mapper.mapName(it)
                                val mappedDesc = mapper.mapDesc(it)
                                refmap.addProperty(targetName, "$mappedName$mappedDesc")
                            }
                            if (target.isPresent) {
                                return
                            }
                        }
                        logger.warn(
                            "Failed to resolve invoker $targetName ($name$descriptor) in mixin ${
                                mixinName.replace(
                                    '/',
                                    '.'
                                )
                            }"
                        )
                    }
                }
            }

            fun visitInject(visitor: AnnotationVisitor) = object : AnnotationVisitor(Constant.ASM_VERSION, visitor) {
                val remapInject = AtomicBoolean(remap.get())
                var targetNames = mutableListOf<String>()
                override fun visit(name: String, value: Any) {
                    super.visit(name, value)
                    if (name == AnnotationElement.REMAP) remapInject.set(value as Boolean)
                }

                override fun visitArray(name: String): AnnotationVisitor {
                    val delegate = super.visitArray(name)
                    return when (name) {
                        AnnotationElement.AT -> {
                            ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { visitAt(it, remap) }
                        }

                        AnnotationElement.SLICE -> {
                            ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { visitSlice(it, remap) }
                        }

                        AnnotationElement.TARGET -> {
                            ArrayVisitorWrapper(Constant.ASM_VERSION, delegate) { visitDesc(it, remap) }
                        }

                        AnnotationElement.METHOD -> {
                            object : AnnotationVisitor(Constant.ASM_VERSION, delegate) {
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

                val callbackInfo = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;"
                val callbackInfoReturn = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;"

                private fun parseCIRVal(): String {
                    val remain = signature!!.split("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable<")[1]
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
                    return valBuild
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

                private fun stripCallbackInfoFromDesc(): Set<String> {
                    val desc = descriptor.replace(callbackInfo, "").replace(callbackInfoReturn, "")
                    if (descriptor.contains(callbackInfoReturn)) {
                        val returnType = parseCIRVal()
                        val rets = setOfNotNull(
                            desc.replace(")V", ")$returnType"),
                            toPrimitive(returnType)?.let { desc.replace(")V", ")${it}") })
                        logger.info("Found returnable inject, signatures $rets, return type $returnType")
                        return rets
                    }
                    return setOf(desc)
                }

                override fun visitEnd() {
                    super.visitEnd()
                    if (remapInject.get()) {
                        targetNames.forEach { targetMethod ->
                            if (targetMethod.equals("<init>") || targetMethod.equals("<clinit>") ||
                                targetMethod.equals("<init>*")) {
                                return@forEach
                            }
                            val (targetName, targetDescs) = if (targetMethod.contains("(")) {
                                val n = targetMethod.split("(")
                                (n[0] to setOf("(${n[1]}"))
                            } else {
                                (targetMethod to stripCallbackInfoFromDesc() + setOf(null))
                            }
                            for (targetDesc in targetDescs) {
                                for (targetClass in classValues + classTargets.map { it.replace('.', '/') }) {
                                    val target = resolver.resolveMethod(
                                        targetClass,
                                        targetName,
                                        targetDesc,
                                        ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE
                                    )
                                    target.ifPresent {
                                        val mappedClass = resolver.resolveClass(targetClass).map { mapper.mapName(it) }.orElse(targetClass)
                                        val mappedName = mapper.mapName(it)
                                        val mappedDesc = mapper.mapDesc(it)
                                        refmap.addProperty(targetMethod, "L$mappedClass;$mappedName$mappedDesc")
                                    }
                                    if (target.isPresent) {
                                        return@forEach
                                    }
                                }
                            }
                            logger.warn(
                                "Failed to resolve Inject $targetMethod ($name$descriptor) $signature in mixin ${
                                    mixinName.replace(
                                        '/',
                                        '.'
                                    )
                                }"
                            )
                        }
                    }
                }
            }

            fun visitModifyArg(visitor: AnnotationVisitor) = object : AnnotationVisitor(Constant.ASM_VERSION, visitor) {
                val remapModifyArg = AtomicBoolean(remap.get())
                var targetNames = mutableListOf<String>()

                override fun visit(name: String, value: Any) {
                    super.visit(name, value)
                    if (name == AnnotationElement.REMAP) remapModifyArg.set(value as Boolean)
                }

                override fun visitAnnotation(name: String, descriptor: String): AnnotationVisitor {
                    return when (name) {
                        AnnotationElement.AT -> {
                            visitAt(super.visitAnnotation(name, descriptor), remap)
                        }

                        AnnotationElement.SLICE -> {
                            visitSlice(super.visitAnnotation(name, descriptor), remap)
                        }

                        else -> {
                            super.visitAnnotation(name, descriptor)
                        }
                    }
                }

                override fun visitArray(name: String): AnnotationVisitor {
                    return when (name) {
                        AnnotationElement.TARGET -> {
                            ArrayVisitorWrapper(Constant.ASM_VERSION, super.visitArray(name)) { visitDesc(it, remap) }
                        }

                        AnnotationElement.METHOD -> {
                            object : AnnotationVisitor(Constant.ASM_VERSION, super.visitArray(name)) {
                                override fun visit(name: String?, value: Any) {
                                    super.visit(name, value)
                                    targetNames.add(value as String)
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
                    if (remapModifyArg.get()) {
                        targetNames.forEach { targetName ->
                            for (targetClass in classValues + classTargets.map { it.replace('.', '/') }) {
                                val targetDesc = if (targetName.contains("(")) {
                                    "(" + targetName.split("(")[1]
                                } else {
                                    null
                                }

                                val target = resolver.resolveMethod(
                                    targetClass,
                                    targetName,
                                    targetDesc,
                                    ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE
                                )
                                target.ifPresent {
                                    val mappedClass = resolver.resolveClass(targetClass).map { mapper.mapName(it) }.orElse(targetClass)
                                    val mappedName = mapper.mapName(it)
                                    val mappedDesc = mapper.mapDesc(it)
                                    refmap.addProperty(targetName, "L$mappedClass;$mappedName$mappedDesc")
                                }
                                if (target.isPresent) {
                                    return@forEach
                                }
                            }
                            logger.warn(
                                "Failed to resolve ModifyArg(s)/Redirect $targetName ($name$descriptor) in mixin ${
                                    mixinName.replace(
                                        '/',
                                        '.'
                                    )
                                }"
                            )
                        }
                    }
                }
            }

            fun visitModifyArgs(visitor: AnnotationVisitor) = visitModifyArg(visitor)

            fun visitModifyConstant(visitor: AnnotationVisitor) =
                object : AnnotationVisitor(Constant.ASM_VERSION, visitor) {
                    val remapModifyConstant = AtomicBoolean(remap.get())
                    var targetNames = mutableListOf<String>()

                    override fun visit(name: String, value: Any) {
                        super.visit(name, value)
                        if (name == AnnotationElement.REMAP) remapModifyConstant.set(value as Boolean)
                    }

                    override fun visitArray(name: String): AnnotationVisitor {
                        return if (name == AnnotationElement.TARGET) {
                            ArrayVisitorWrapper(Constant.ASM_VERSION, super.visitArray(name)) { visitDesc(it, remap) }
                        } else if (name == AnnotationElement.SLICE) {
                            ArrayVisitorWrapper(Constant.ASM_VERSION, super.visitArray(name)) { visitSlice(it, remap) }
                        } else if (name == AnnotationElement.METHOD) {
                            object : AnnotationVisitor(Constant.ASM_VERSION, super.visitArray(name)) {
                                override fun visit(name: String?, value: Any) {
                                    super.visit(name, value)
                                    targetNames.add(value as String)
                                }
                            }
                        } else {
                            super.visitArray(name)
                        }
                    }

                    override fun visitEnd() {
                        super.visitEnd()
                        if (remapModifyConstant.get()) {
                            targetNames.forEach { targetName ->
                                for (targetClass in classValues + classTargets.map { it.replace('.', '/') }) {
                                    val targetDesc = if (targetName.contains("(")) {
                                        "(" + targetName.split("(")[1]
                                    } else {
                                        null
                                    }

                                    val target = resolver.resolveMethod(
                                        targetClass,
                                        targetName,
                                        targetDesc,
                                        ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE
                                    )


                                    target.ifPresent {
                                        val mappedClass = resolver.resolveClass(targetClass).map { mapper.mapName(it) }.orElse(targetClass)
                                        val mappedName = mapper.mapName(it)
                                        val mappedDesc = mapper.mapDesc(it)
                                        refmap.addProperty(targetName, "L$mappedClass;$mappedName$mappedDesc")
                                    }
                                    if (target.isPresent) {
                                        return@forEach
                                    }
                                }
                                logger.warn(
                                    "Failed to resolve ModifyConstant $targetName ($name$descriptor) in mixin ${
                                        mixinName.replace(
                                            '/',
                                            '.'
                                        )
                                    }"
                                )
                            }
                        }
                    }
                }

            fun visitModifyVariable(visitor: AnnotationVisitor) = visitModifyArg(visitor)

            fun visitRedirect(visitor: AnnotationVisitor) = visitModifyArg(visitor)
        }
    }

    fun visitAt(visitor: AnnotationVisitor, remap: AtomicBoolean) =
        object : AnnotationVisitor(Constant.ASM_VERSION, visitor) {
            val remapAt = AtomicBoolean(remap.get())
            var targetName: String? = null
            override fun visit(name: String, value: Any) {
                super.visit(name, value)
                if (name == AnnotationElement.REMAP) remapAt.set(value as Boolean)
                if (name == AnnotationElement.TARGET) targetName = value as String
            }

            override fun visitAnnotation(name: String, descriptor: String): AnnotationVisitor {
                return if (name == AnnotationElement.DESC) {
                    visitDesc(super.visitAnnotation(name, descriptor), remapAt)
                } else {
                    logger.warn("Found annotation in target descriptor: $name $descriptor")
                    super.visitAnnotation(name, descriptor)
                }
            }

            val targetField = Regex("^(L[^;]+;|[^.]+?\\.)([^:]+):(.+)$")
            val targetMethod = Regex("^(L[^;]+;|[^.]+?\\.)([^(]+)(.+)$")

            override fun visitEnd() {
                super.visitEnd()
                if (remapAt.get() && targetName != null) {
                    val matchFd = targetField.matchEntire(targetName!!)
                    if (matchFd != null) {
                        val targetOwner = matchFd.groupValues[1].let {
                            if (it.startsWith("L") && it.endsWith(";")) it.substring(
                                1,
                                it.length - 1
                            ) else it.substring(0, it.length - 1)
                        }
                        val targetName = matchFd.groupValues[2]
                        val targetDesc = matchFd.groupValues[3]
                        val target = resolver.resolveField(
                            targetOwner,
                            targetName,
                            targetDesc,
                            ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE
                        )
                        val targetClass = resolver.resolveClass(targetOwner)
                        targetClass.ifPresent { clz ->
                            target.ifPresent {
                                val mappedOwner = mapper.mapName(clz)
                                val mappedName = mapper.mapName(it)
                                val mappedDesc = mapper.mapDesc(it)
                                refmap.addProperty(this.targetName, "L$mappedOwner;$mappedName:$mappedDesc")
                            }
                        }
                        if (!target.isPresent || !targetClass.isPresent) {
                            logger.warn(
                                "Failed to resolve At target $targetName in mixin ${
                                    mixinName.replace(
                                        '/',
                                        '.'
                                    )
                                }"
                            )
                        }
                        return
                    }
                    val matchMd = targetMethod.matchEntire(targetName!!)
                    if (matchMd != null) {
                        val targetOwner = matchMd.groupValues[1].let {
                            if (it.startsWith("L") && it.endsWith(";")) it.substring(
                                1,
                                it.length - 1
                            ) else it.substring(0, it.length - 1)
                        }
                        val targetName = matchMd.groupValues[2]
                        val targetDesc = matchMd.groupValues[3]
                        val target = resolver.resolveMethod(
                            targetOwner,
                            targetName,
                            targetDesc,
                            ResolveUtility.FLAG_UNIQUE or ResolveUtility.FLAG_RECURSIVE
                        )
                        val targetClass = resolver.resolveClass(targetOwner)
                        targetClass.ifPresent { clz ->
                            target.ifPresent {
                                val mappedOwner = mapper.mapName(clz)
                                val mappedName = mapper.mapName(it)
                                val mappedDesc = mapper.mapDesc(it)
                                refmap.addProperty(this.targetName, "L$mappedOwner;$mappedName$mappedDesc")
                            }
                        }
                        if (!target.isPresent || !targetClass.isPresent) {
                            logger.warn(
                                "Failed to resolve At target $targetName in mixin ${
                                    mixinName.replace(
                                        '/',
                                        '.'
                                    )
                                }"
                            )
                        }
                        return
                    }

                    logger.warn(
                        "Failed to parse target descriptor: $targetName in mixin ${
                            mixinName.replace(
                                '/',
                                '.'
                            )
                        }"
                    )
                }
            }
        }

    fun visitDesc(visitor: AnnotationVisitor, remap: AtomicBoolean) =
        object : AnnotationVisitor(Constant.ASM_VERSION, visitor) {
            val remapDesc = AtomicBoolean(remap.get())
            override fun visit(name: String, value: Any) {
                TODO()
            }

            override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor {
                TODO()
            }
        }

    fun visitSlice(visitor: AnnotationVisitor, remap: AtomicBoolean) =
        object : AnnotationVisitor(Constant.ASM_VERSION, visitor) {
            override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor {
                return if (name == AnnotationElement.FROM || name == AnnotationElement.TO) {
                    visitAt(super.visitAnnotation(name, descriptor), remap)
                } else {
                    super.visitAnnotation(name, descriptor)
                }
            }
        }

    override fun visitEnd() {
        super.visitEnd()
        onEnd()
    }
}

class ArrayVisitorWrapper(
    val api: Int,
    delegate: AnnotationVisitor,
    val delegateCreator: (AnnotationVisitor) -> AnnotationVisitor
) : AnnotationVisitor(api, delegate) {
    override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor {
        return delegateCreator(super.visitAnnotation(name, descriptor))
    }
}
