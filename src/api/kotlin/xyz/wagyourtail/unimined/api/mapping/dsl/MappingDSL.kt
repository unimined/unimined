package xyz.wagyourtail.unimined.api.mapping.dsl

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.util.Scoped
import xyz.wagyourtail.unimined.mapping.util.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.FieldVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MethodVisitor

@Scoped
class MappingDSL(val visitor: MappingVisitor) {

    var mappings = listOf<Namespace>()

    fun namespace(vararg namespaces: String) {
        mappings = namespaces.map { Namespace(it) }.toList()
    }

    @JvmOverloads
    fun c(vararg names: String?, block: ClassDSL.() -> Unit = {}) {
        visitor.visitClass(mappings.zip(names.toList()).toMap().filterNotNullValues().mapValues { InternalName.read(it.value) })?.let {
            ClassDSL(it).block()
        }
    }

    fun c(
        names: List<String?>,
        @DelegatesTo(ClassDSL::class, strategy = Closure.DELEGATE_FIRST)
        block: Closure<*>
    ) {
        c(*names.toTypedArray()) {
            block.delegate = this
            block.call()
        }
    }

    @Scoped
    inner class ClassDSL(val visitor: ClassVisitor) {

        @JvmOverloads
        fun f(vararg names: String?, block: FieldDSL.() -> Unit = {}) {
            visitor.visitField(this@MappingDSL.mappings.zip(names.toList()).toMap().filterNotNullValues().mapValues {
                if (it.value.contains(";")) {
                    val split = it.value.split(";", limit = 2)
                    split[0] to FieldDescriptor.read(split[1])
                } else {
                    it.value to null
                }
            })?.let {
                FieldDSL(it).block()
            }
        }

        fun f(
            names: List<String?>,
            @DelegatesTo(FieldDSL::class, strategy = Closure.DELEGATE_FIRST)
            block: Closure<*>
        ) {
            f(*names.toTypedArray()) {
                block.delegate = this
                block.call()
            }
        }

        @JvmOverloads
        fun m(vararg names: String?, block: MethodDSL.() -> Unit = {}) {
            visitor.visitMethod(this@MappingDSL.mappings.zip(names.toList()).toMap().filterNotNullValues().mapValues {
                if (it.value.contains(";")) {
                    val split = it.value.split(";", limit = 2)
                    split[0] to MethodDescriptor.read(split[1])
                } else {
                    it.value to null
                }
            })?.let {
                MethodDSL(it).block()
            }
        }

        fun m(
            names: List<String?>,
            @DelegatesTo(MethodDSL::class, strategy = Closure.DELEGATE_FIRST)
            block: Closure<*>
        ) {
            m(*names.toTypedArray()) {
                block.delegate = this
                block.call()
            }
        }

    }

    @Scoped
    class FieldDSL(val visitor: FieldVisitor) {

    }

    @Scoped
    class MethodDSL(val visitor: MethodVisitor) {

    }
}