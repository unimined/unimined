@file:Suppress("unused")

package xyz.wagyourtail.unimined.providers.patch.remap.stubmappings

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.format.Tiny2Writer
import java.io.StringWriter
import java.security.MessageDigest

fun ByteArray.toHex() = joinToString(separator = "") { byte -> "%02x".format(byte) }

class MemoryMapping {
    private val classes = mutableListOf<ClassMapping>()
    var srcNamespace = "official"

    fun c(srcName: String, vararg targets: Pair<String, String>) {
        classes.add(ClassMapping(srcName, *targets))
    }

    fun c(srcName: String, vararg targets: Pair<String, String>, action: ClassMapping.() -> Unit) {
        classes.add(ClassMapping(srcName, *targets).apply(action))
    }

    fun c(srcName: String, targets: Map<String, String>) {
        classes.add(ClassMapping(srcName, targets))
    }

    fun c(srcName: String, targets: Map<String, String>, action: ClassMapping.() -> Unit) {
        classes.add(ClassMapping(srcName, targets).apply(action))
    }

    @JvmName("withMappingsKt")
    fun withMappings(vararg mappings: String, action: MemoryMappingWithMappings.() -> Unit) {
        action(MemoryMappingWithMappings(this, *mappings))
    }

    fun withMappings(mappings: List<String>, @DelegatesTo(value = MemoryMappingWithMappings::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
        action.delegate = MemoryMappingWithMappings(this, *mappings.toTypedArray())
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }

    private fun getNamespaces(): Set<String> {
        val namespaces = mutableSetOf<String>()
        classes.forEach { clazz ->
            namespaces.addAll(clazz.getNamespaces())
        }
        return namespaces
    }

    fun visit(visitor: MappingVisitor) {
        val namespaces = getNamespaces().mapIndexed { i: Int, s: String -> s to i }
        if (visitor.visitHeader()) {
            visitor.visitNamespaces(srcNamespace, namespaces.map { it.first })
        }

        if (visitor.visitContent()) {
            classes.forEach { clazz ->
                clazz.visit(visitor, mapOf(*namespaces.toTypedArray()))
            }
        }
    }

    val hash by lazy {
        val sha = MessageDigest.getInstance("SHA-256")
        val stringWriter = StringWriter()
        visit(Tiny2Writer(stringWriter, false))
        sha.update(stringWriter.toString().toByteArray())
        sha.digest().toHex().substring(0..8)
    }
}

class MemoryMappingWithMappings(val memoryMapping: MemoryMapping, vararg val mappings: String) {

    fun c(srcName: String, vararg targets: String) {
        memoryMapping.c(srcName, *(mappings zip targets).toTypedArray())
    }

    fun c(srcName: String, vararg targets: String, action: ClassMappingWithMappings.() -> Unit) {
        memoryMapping.c(srcName, *(mappings zip targets).toTypedArray()) {
            action(ClassMappingWithMappings(this, *mappings))
        }
    }
}

abstract class MappingMember(val srcName: String, vararg targets: Pair<String, String>) {
    constructor(srcName: String, targets: Map<String, String>) : this(srcName, *targets.toList().toTypedArray())

    val targets = mutableMapOf(*targets)

    internal open fun getNamespaces(): Set<String> {
        return targets.keys
    }

    internal abstract fun visit(visitor: MappingVisitor, namespaces: Map<String, Int>)
}

class ClassMapping(srcName: String, vararg targets: Pair<String, String>) : MappingMember(srcName, *targets) {
    constructor(srcName: String, targets: Map<String, String>) : this(srcName, *targets.toList().toTypedArray())

    private val fields = mutableListOf<FieldMapping>()
    private val methods = mutableListOf<MethodMapping>()

    fun f(srcName: String, srcDesc: String, vararg targets: Pair<String, String>) {
        fields.add(FieldMapping(srcName, srcDesc, *targets))
    }

    fun f(srcName: String, srcDesc: String, targets: Map<String, String>) {
        fields.add(FieldMapping(srcName, srcDesc, targets))
    }

    fun m(srcName: String, srcDesc: String, vararg targets: Pair<String, String>) {
        methods.add(MethodMapping(srcName, srcDesc, *targets))
    }

    fun m(srcName: String, srcDesc: String, vararg targets: Pair<String, String>, action: MethodMapping.() -> Unit = {}) {
        methods.add(MethodMapping(srcName, srcDesc, *targets).apply(action))
    }

    fun m(srcName: String, srcDesc: String, targets: Map<String, String>) {
        methods.add(MethodMapping(srcName, srcDesc, targets))
    }

    fun m(srcName: String, srcDesc: String, targets: Map<String, String>, action: MethodMapping.() -> Unit = {}) {
        methods.add(MethodMapping(srcName, srcDesc, targets).apply(action))
    }

    @JvmName("withMappingsKt")
    fun withMappings(vararg mappings: String, action: ClassMappingWithMappings.() -> Unit) {
        action(ClassMappingWithMappings(this, *mappings))
    }

    fun withMappings(mappings: List<String>, @DelegatesTo(value = ClassMappingWithMappings::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
        action.delegate = ClassMappingWithMappings(this, *mappings.toTypedArray())
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }

    override fun getNamespaces(): Set<String> {
        val namespaces = super.getNamespaces().toMutableSet()
        fields.forEach { field ->
            namespaces.addAll(field.getNamespaces())
        }
        methods.forEach { method ->
            namespaces.addAll(method.getNamespaces())
        }
        return namespaces
    }

    override fun visit(visitor: MappingVisitor, namespaces: Map<String, Int>) {
        if (visitor.visitClass(srcName)) {
            for (target in targets) {
                visitor.visitDstName(MappedElementKind.CLASS, namespaces[target.key]!!, target.value)
            }
            if (visitor.visitElementContent(MappedElementKind.CLASS)) {
                fields.forEach { field ->
                    field.visit(visitor, namespaces)
                }
                methods.forEach { method ->
                    method.visit(visitor, namespaces)
                }
            }
        }
    }
}

class ClassMappingWithMappings(val classMapping: ClassMapping, vararg val mappings: String) {
    fun f(srcName: String, srcDesc: String, vararg targets: String) {
        classMapping.f(srcName, srcDesc, *(mappings zip targets).toTypedArray())
    }

    fun m(srcName: String, srcDesc: String, vararg targets: String) {
        classMapping.m(srcName, srcDesc, *(mappings zip targets).toTypedArray())
    }

    fun m(srcName: String, srcDesc: String, vararg targets: String, action: MethodMappingWithMappings.() -> Unit) {
        classMapping.m(srcName, srcDesc, *(mappings zip targets).toTypedArray()) {
            action(MethodMappingWithMappings(this, *mappings))
        }
    }
}

class FieldMapping(srcName: String, val srcDesc: String, vararg targets: Pair<String, String>) : MappingMember(srcName, *targets) {
    constructor(srcName: String, srcDesc: String, targets: Map<String, String>) : this(srcName, srcDesc, *targets.toList().toTypedArray())

    override fun visit(visitor: MappingVisitor, namespaces: Map<String, Int>) {
        if (visitor.visitField(srcName, srcDesc)) {
            for (target in targets) {
                visitor.visitDstName(MappedElementKind.FIELD, namespaces[target.key]!!, target.value)
            }
        }
    }


}

class MethodMapping(srcName: String, val srcDesc: String, vararg targets: Pair<String, String>) : MappingMember(srcName, *targets) {
    constructor(srcName: String, srcDesc: String, targets: Map<String, String>) : this(srcName, srcDesc, *targets.toList().toTypedArray())

    private val params = mutableMapOf<Int, MutableMap<String, String>>()

    fun p(index: Int, vararg targets: Pair<String, String>) {
        params.computeIfAbsent(index) { mutableMapOf() }.putAll(targets)
    }

    fun p(index: Int, targets: Map<String, String>) {
        params.computeIfAbsent(index) { mutableMapOf() }.putAll(targets)
    }

    @JvmName("withMappingsKt")
    fun withMappings(vararg mappings: String, action: MethodMappingWithMappings.() -> Unit) {
        action(MethodMappingWithMappings(this, *mappings))
    }

    fun withMappings(mappings: List<String>, @DelegatesTo(value = MethodMappingWithMappings::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
        action.delegate = MethodMappingWithMappings(this, *mappings.toTypedArray())
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }

    override fun getNamespaces(): Set<String> {
        val namespaces = super.getNamespaces().toMutableSet()
        params.forEach { (_, targets) ->
            namespaces.addAll(targets.keys)
        }
        return namespaces
    }

    override fun visit(visitor: MappingVisitor, namespaces: Map<String, Int>) {
        if (visitor.visitMethod(srcName, srcDesc)) {
            for (target in targets) {
                visitor.visitDstName(MappedElementKind.METHOD, namespaces[target.key]!!, target.value)
            }
            if (visitor.visitElementContent(MappedElementKind.METHOD)) {
                params.forEach { (index, targets) ->
                    if (visitor.visitMethodArg(index, -1, null)) {
                        targets.forEach { (key, value) ->
                            visitor.visitDstName(MappedElementKind.METHOD_ARG, namespaces[key]!!, value)
                        }
                    }
                }
            }
        }
    }


}

class MethodMappingWithMappings(val methodMapping: MethodMapping, vararg val mappings: String) {
    fun p(index: Int, vararg targets: String) {
        methodMapping.p(index, *(mappings zip targets).toTypedArray())
    }
}

