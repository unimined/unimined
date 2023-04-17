@file:Suppress("unused")

package xyz.wagyourtail.unimined.api.mappings

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.format.Tiny2Writer
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.util.toHex
import java.io.StringWriter
import java.security.MessageDigest

/**
 * A class to represent minecraft mappings.
 * @since 0.1.0
 */
class MemoryMapping {
    var srcNamespace = "official"
    private val classesBySrc = mutableMapOf<String, MutableList<ClassMapping>>()

    private val classes
        get() = bySrc(srcNamespace)

    @ApiStatus.Internal
    fun bySrc(srcName: String) = classesBySrc.computeIfAbsent(srcName) { mutableListOf() }

    /**
     * Add a class mapping.
     * @param srcName The source name of the class.
     * @param targets namespace -> target name
     * @since 0.1.0
     */
    fun c(srcName: String, vararg targets: Pair<String, String>) {
        classes.add(ClassMapping(srcName, *targets))
    }

    /**
     * Add a class mapping.
     * @param srcName The source name of the class.
     * @param targets namespace -> target name.
     * @param action A closure to add mappings to the class.
     * @since 0.1.0
     */
    fun c(srcName: String, vararg targets: Pair<String, String>, action: ClassMapping.() -> Unit) {
        classes.add(ClassMapping(srcName, *targets).apply(action))
    }

    /**
     * Add a class mapping.
     * @param srcName The source name of the class.
     * @param targets namespace -> target name
     * @since 0.1.0
     */
    fun c(srcName: String, targets: Map<String, String>) {
        classes.add(ClassMapping(srcName, targets))
    }

    /**
     * Add a class mapping.
     * @param srcName The source name of the class.
     * @param targets namespace -> target name.
     * @param action A closure to add mappings to the class.
     * @since 0.1.0
     */
    fun c(srcName: String, targets: Map<String, String>, action: ClassMapping.() -> Unit) {
        classes.add(ClassMapping(srcName, targets).apply(action))
    }

    /**
     * @since 0.4.10
     */
     fun c(srcName: String, targets: Map<String, String>, @DelegatesTo(value = ClassMapping::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
        c(srcName, targets) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * bind a set of target mappings
     * @param mappings The mappings to bind.
     * @since 0.1.0
     * @see MemoryMappingWithMappings
     */
    @JvmName("withMappingsKt")
    fun withMappings(vararg mappings: String, action: MemoryMappingWithMappings.() -> Unit) {
        srcNamespace = "official"
        action(MemoryMappingWithMappings(this, *mappings))
    }

    /**
     * @since 0.4.10
     */
    @JvmName("withMappingsKt")
    fun withMappings(src: String, vararg mappings: String, action: MemoryMappingWithMappings.() -> Unit) {
        srcNamespace = src
        action(MemoryMappingWithMappings(this, *mappings))
    }

    /**
     * bind a set of target mappings
     * @param mappings The mappings to bind.
     * @since 0.1.0
     * @see MemoryMappingWithMappings
     */
    fun withMappings(
        mappings: List<String>,
        @DelegatesTo(value = MemoryMappingWithMappings::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>
    ) {
        withMappings(*mappings.toTypedArray()) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * @since 0.4.10
     */
    fun withMappings(
        src: String,
        mappings: List<String>,
        @DelegatesTo(value = MemoryMappingWithMappings::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>
    ) {
        srcNamespace = src
        withMappings(*mappings.toTypedArray()) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    private fun getNamespaces(): Set<String> {
        val namespaces = mutableSetOf<String>()
        classes.forEach { clazz ->
            namespaces.addAll(clazz.getNamespaces())
        }
        return namespaces
    }

    @ApiStatus.Internal
    fun visit(visitor: MappingVisitor) {
        for (src in classesBySrc.keys) {
            srcNamespace = src
            val namespaces = getNamespaces().mapIndexed { i: Int, s: String -> s to i }
            if (visitor.visitHeader()) {
                visitor.visitNamespaces(srcNamespace, namespaces.map { it.first })
            }

            if (visitor.visitContent()) {
                classes.forEach { clazz ->
                    clazz.visit(visitor, mapOf(*namespaces.toTypedArray()))
                }
            }

            visitor.visitEnd()
        }
    }

    @get:ApiStatus.Internal
    val hash by lazy {
        val sha = MessageDigest.getInstance("SHA-256")
        val stringWriter = StringWriter()
        visit(Tiny2Writer(stringWriter, false))
        sha.update(stringWriter.toString().toByteArray())
        sha.digest().toHex().substring(0..8)
    }
}

/**
 * A class to represent minecraft mappings.
 * with a bound set of mappings
 * @since 0.1.0
 */
class MemoryMappingWithMappings(val memoryMapping: MemoryMapping, vararg val mappings: String) {

    /**
     * Add a class mapping.
     * @param srcName The source name of the class.
     * @param targets target names (in the order of the mappings passed to [MemoryMappingWithMappings])
     * @since 0.1.0
     */
    fun c(srcName: String, vararg targets: String) {
        memoryMapping.c(srcName, *(mappings zip targets).toTypedArray())
    }

    /**
     * Add a class mapping.
     * @param srcName The source name of the class.
     * @param targets target names (in the order of the mappings passed to [MemoryMappingWithMappings])
     * @param action A closure to add mappings to the class.
     * @since 0.1.0
     */
    fun c(srcName: String, vararg targets: String, action: ClassMappingWithMappings.() -> Unit) {
        memoryMapping.c(srcName, *(mappings zip targets).toTypedArray()) {
            action(ClassMappingWithMappings(this, *mappings))
        }
    }

    /**
     * @since 0.4.10
     */
     fun c(srcName: String, targets: List<String>) {
        memoryMapping.c(srcName, *(mappings zip targets).toTypedArray())
     }

    /**
     * @since 0.4.10
     */
    fun c(srcName: String, targets: List<String>, @DelegatesTo(value = ClassMappingWithMappings::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
        c(srcName, *targets.toTypedArray()) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * @since 0.4.10
     */

}

@ApiStatus.Internal
abstract class MappingMember(val srcName: String, vararg targets: Pair<String, String>) {
    constructor(srcName: String, targets: Map<String, String>): this(srcName, *targets.toList().toTypedArray())

    internal val targets = mutableMapOf(*targets)

    internal open fun getNamespaces(): Set<String> {
        return targets.keys
    }

    internal abstract fun visit(visitor: MappingVisitor, namespaces: Map<String, Int>)
}

/**
 * A class to represent a class mapping.
 * @since 0.1.0
 */
class ClassMapping(srcName: String, vararg targets: Pair<String, String>): MappingMember(srcName, *targets) {
    constructor(srcName: String, targets: Map<String, String>): this(srcName, *targets.toList().toTypedArray())

    private val fields = mutableListOf<FieldMapping>()
    private val methods = mutableListOf<MethodMapping>()

    /**
     * Add a field mapping.
     * @param srcName The source name of the field.
     * @param srcDesc The source descriptor of the field.
     * @param targets namespace -> target name
     * @since 0.1.0
     */
    fun f(srcName: String, srcDesc: String, vararg targets: Pair<String, String>) {
        fields.add(FieldMapping(srcName, srcDesc, *targets))
    }

    /**
     * Add a field mapping.
     * @param srcName The source name of the field.
     * @param srcDesc The source descriptor of the field.
     * @param targets namespace -> target name
     * @since 0.1.0
     */
    fun f(srcName: String, srcDesc: String, targets: Map<String, String>) {
        fields.add(FieldMapping(srcName, srcDesc, targets))
    }

    /**
     * Add a method mapping.
     * @param srcName The source name of the field.
     * @param srcDesc The source descriptor of the field.
     * @param targets namespace -> target name
     * @since 0.1.0
     */
    fun m(srcName: String, srcDesc: String, vararg targets: Pair<String, String>) {
        methods.add(MethodMapping(srcName, srcDesc, *targets))
    }

    /**
     * Add a method mapping.
     * @param srcName The source name of the field.
     * @param srcDesc The source descriptor of the field.
     * @param targets namespace -> target name
     * @param action A closure to add mappings to the method.
     * @since 0.1.0
     */
    fun m(
        srcName: String,
        srcDesc: String,
        vararg targets: Pair<String, String>,
        action: MethodMapping.() -> Unit = {}
    ) {
        methods.add(MethodMapping(srcName, srcDesc, *targets).apply(action))
    }

    /**
     * Add a method mapping.
     * @param srcName The source name of the field.
     * @param srcDesc The source descriptor of the field.
     * @param targets namespace -> target name
     * @since 0.1.0
     */
    fun m(srcName: String, srcDesc: String, targets: Map<String, String>) {
        methods.add(MethodMapping(srcName, srcDesc, targets))
    }

    /**
     * Add a method mapping.
     * @param srcName The source name of the field.
     * @param srcDesc The source descriptor of the field.
     * @param targets namespace -> target name
     * @param action A closure to add mappings to the method.
     * @since 0.1.0
     */
    fun m(srcName: String, srcDesc: String, targets: Map<String, String>, action: MethodMapping.() -> Unit = {}) {
        methods.add(MethodMapping(srcName, srcDesc, targets).apply(action))
    }

    /**
     * @since 0.4.10
     */
    fun m(srcName: String, srcDesc: String, targets: Map<String, String>, @DelegatesTo(value = MethodMapping::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
        m(srcName, srcDesc, *targets.toList().toTypedArray()) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    /**
     * Bind a set of mappings to this class.
     * @param mappings The mappings to bind.
     * @param action A closure to add mappings to the class.
     * @since 0.1.0
     * @see ClassMappingWithMappings
     */
    @JvmName("withMappingsKt")
    fun withMappings(vararg mappings: String, action: ClassMappingWithMappings.() -> Unit) {
        action(ClassMappingWithMappings(this, *mappings))
    }

    /**
     * Bind a set of mappings to this class.
     * @param mappings The mappings to bind.
     * @param action A closure to add mappings to the class.
     * @since 0.1.0
     * @see ClassMappingWithMappings
     */
    fun withMappings(
        mappings: List<String>,
        @DelegatesTo(value = ClassMappingWithMappings::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>
    ) {
        action.delegate = ClassMappingWithMappings(this, *mappings.toTypedArray())
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }

    @ApiStatus.Internal
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

    @ApiStatus.Internal
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

/**
 * A class to represent a class mapping.
 * with a bound set of mappings
 * @since 0.1.0
 */
class ClassMappingWithMappings(val classMapping: ClassMapping, vararg val mappings: String) {
    /**
     * Add a field mapping.
     * @param srcName The source name of the field.
     * @param srcDesc The source descriptor of the field.
     * @param targets target names (in the order of the mappings passed to [ClassMappingWithMappings])
     * @since 0.1.0
     */
    fun f(srcName: String, srcDesc: String, vararg targets: String) {
        classMapping.f(srcName, srcDesc, *(mappings zip targets).toTypedArray())
    }

    /**
     * @since 0.4.10
     */
    fun f(srcName: String, srcDesc: String, targets: List<String>) {
        f(srcName, srcDesc, *targets.toTypedArray())
    }

    /**
     * Add a field mapping.
     * @param srcName The source name of the field.
     * @param srcDesc The source descriptor of the field.
     * @param targets target names (in the order of the mappings passed to [ClassMappingWithMappings])
     * @since 0.1.0
     */
    fun m(srcName: String, srcDesc: String, vararg targets: String) {
        classMapping.m(srcName, srcDesc, *(mappings zip targets).toTypedArray())
    }

    /**
     * @since 0.4.10
     */
    fun m(srcName: String, srcDesc: String, targets: List<String>) {
        m(srcName, srcDesc, *targets.toTypedArray())
    }

    /**
     * Add a method mapping.
     * @param srcName The source name of the field.
     * @param srcDesc The source descriptor of the field.
     * @param targets target names (in the order of the mappings passed to [ClassMappingWithMappings])
     * @param action A closure to add mappings to the method.
     * @since 0.1.0
     */
    fun m(srcName: String, srcDesc: String, vararg targets: String, action: MethodMappingWithMappings.() -> Unit) {
        classMapping.m(srcName, srcDesc, *(mappings zip targets).toTypedArray()) {
            action(MethodMappingWithMappings(this, *mappings))
        }
    }

    /**
     * @since 0.4.10
     */
    fun m(srcName: String, srcDesc: String, targets: List<String>, @DelegatesTo(value = MethodMappingWithMappings::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>) {
        m(srcName, srcDesc, *targets.toTypedArray()) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }
}

@ApiStatus.Internal
class FieldMapping(srcName: String, val srcDesc: String, vararg targets: Pair<String, String>): MappingMember(
    srcName,
    *targets
) {
    constructor(srcName: String, srcDesc: String, targets: Map<String, String>): this(
        srcName,
        srcDesc,
        *targets.toList().toTypedArray()
    )

    @ApiStatus.Internal
    override fun visit(visitor: MappingVisitor, namespaces: Map<String, Int>) {
        if (visitor.visitField(srcName, srcDesc)) {
            for (target in targets) {
                visitor.visitDstName(MappedElementKind.FIELD, namespaces[target.key]!!, target.value)
            }
        }
    }
}

/**
 * A class to represent a method mapping.
 * @since 0.1.0
 */
class MethodMapping(srcName: String, val srcDesc: String, vararg targets: Pair<String, String>): MappingMember(
    srcName,
    *targets
) {
    constructor(srcName: String, srcDesc: String, targets: Map<String, String>): this(
        srcName,
        srcDesc,
        *targets.toList().toTypedArray()
    )

    private val params = mutableMapOf<Int, MutableMap<String, String>>()

    /**
     * Add a parameter mapping.
     * @param index The index of the parameter.
     * @param targets namespace -> target name
     * @since 0.1.0
     */
    fun p(index: Int, vararg targets: Pair<String, String>) {
        params.computeIfAbsent(index) { mutableMapOf() }.putAll(targets)
    }

    /**
     * Add a parameter mapping.
     * @param index The index of the parameter.
     * @param targets namespace -> target name
     * @since 0.1.0
     */
    fun p(index: Int, targets: Map<String, String>) {
        params.computeIfAbsent(index) { mutableMapOf() }.putAll(targets)
    }

    /**
     * Bind a set of mappings to this method.
     * @param mappings The mappings to bind.
     * @param action A closure to add mappings to the method.
     * @since 0.1.0
     * @see MethodMappingWithMappings
     */
    @JvmName("withMappingsKt")
    fun withMappings(vararg mappings: String, action: MethodMappingWithMappings.() -> Unit) {
        action(MethodMappingWithMappings(this, *mappings))
    }

    /**
     * Bind a set of mappings to this method.
     * @param mappings The mappings to bind.
     * @param action A closure to add mappings to the method.
     * @since 0.1.0
     * @see MethodMappingWithMappings
     */
    fun withMappings(
        mappings: List<String>,
        @DelegatesTo(value = MethodMappingWithMappings::class, strategy = Closure.DELEGATE_FIRST) action: Closure<*>
    ) {
        action.delegate = MethodMappingWithMappings(this, *mappings.toTypedArray())
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action.call()
    }

    @ApiStatus.Internal
    override fun getNamespaces(): Set<String> {
        val namespaces = super.getNamespaces().toMutableSet()
        params.forEach { (_, targets) ->
            namespaces.addAll(targets.keys)
        }
        return namespaces
    }

    @ApiStatus.Internal
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

/**
 * A class to represent a method mapping.
 * with a set of mappings bound to it.
 * @since 0.1.0
 */
class MethodMappingWithMappings(val methodMapping: MethodMapping, vararg val mappings: String) {

    /**
     * Add a parameter mapping.
     * @param index The index of the parameter.
     * @param targets target names (in the order of the mappings passed to [MethodMappingWithMappings])
     * @since 0.1.0
     */
    fun p(index: Int, vararg targets: String) {
        methodMapping.p(index, *(mappings zip targets).toTypedArray())
    }

    /**
     * @since 0.4.10
     */
    fun p(index: Int, targets: List<String>) {
        p(index, *targets.toTypedArray())
    }
}

