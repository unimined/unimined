package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.readZipInputStreamFor
import java.nio.file.Path

class ChildMethodStripper(next: MappingVisitor, val minecraft: Path) : ForwardingMappingVisitor(next) {

    val classMap = defaultedMapOf<String, ClassNode?> { srcName ->
        val cNode = ClassNode()
        try {
            minecraft.readZipInputStreamFor("$srcName.class") { ClassReader(it) }.accept(cNode, ClassReader.SKIP_CODE)
            cNode
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    var cName: String? = null

    fun getClassWithCode(srcName: String): ClassNode? {
        val cNode = ClassNode()
        try {
            minecraft.readZipInputStreamFor("$srcName.class") { ClassReader(it) }.accept(cNode, ClassReader.SKIP_DEBUG)
        } catch (e: IllegalArgumentException) {
            return null
        }

        // copy to class map for speed
        val copy = ClassNode()
        for (method in cNode.methods) {
            if (method.instructions.size() > 0) {
                copy.visitMethod(method.access, method.name, method.desc, method.signature, method.exceptions.toTypedArray())
            }
        }
        copy.interfaces = cNode.interfaces
        copy.superName = cNode.superName
        copy.name = cNode.name
        classMap[srcName] = copy

        return cNode
    }

    /*
    def getParentNodes(cNode):
        currentNode = classMap[cNode]
        while currentNode != null:
            for interface in currentNode.interfaces:
                intf = classMap[interface]
                if intf != null:
                    for p in getParentNodes(intf):
                        yield p
            yield currentNode
            currentNode = classMap[currentNode.superName]
     */

    fun getParentNodes(className: String): Sequence<ClassNode> {
        var currentNode: ClassNode? = classMap[className] ?: return emptySequence()
        return generateSequence {
            val c = currentNode
            currentNode = currentNode?.let { classMap[it.superName] }
            c
        }.flatMap { node ->
            node.interfaces
                .mapNotNull { classMap[it] }
                .flatMap { listOf(it) + getParentNodes(it.name) }
        }.drop(1)
    }

    override fun visitClass(srcName: String): Boolean {
        cName = srcName
        return super.visitClass(srcName)
    }

    fun isPrivate(mn: MethodNode): Boolean {
        return mn.access and Opcodes.ACC_PRIVATE != 0
    }

    override fun visitMethod(srcName: String, srcDesc: String): Boolean {
        // check if there's a bridge pointing at the current method and then just yeet if so
        getClassWithCode(cName!!)?.methods?.firstOrNull {
            it.access and Opcodes.ACC_BRIDGE != 0 && it.instructions.any {
                it is MethodInsnNode && it.name == srcName && it.desc == srcDesc
            }
        }?.let {
            return false
        }

        // find if method is on a parent class
        getParentNodes(cName!!).firstOrNull { clazz ->
            clazz.methods.any {
                it.name == srcName && it.desc == srcDesc && !isPrivate(it)
            }
        }?.let {
            return false
        }

        return super.visitMethod(srcName, srcDesc)
    }
}