package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.readZipInputStreamFor
import java.nio.file.Path

class ChildMethodStripper(next: MappingVisitor, val minecraft: Path) : ForwardingMappingVisitor(next) {

    val classMap = defaultedMapOf<String, ClassNode?> { srcName ->
        val cNode = ClassNode()
        minecraft.readZipInputStreamFor("$srcName.class") { ClassReader(it) }.accept(cNode, ClassReader.SKIP_CODE)
        cNode
    }
    var cName: String? = null

    val parents: Sequence<ClassNode>
        get() = generateSequence { classMap[cName] }

    override fun visitClass(srcName: String): Boolean {
        cName = srcName
        return super.visitClass(srcName)
    }

    override fun visitMethod(srcName: String, srcDesc: String): Boolean {
        // find if method is on a parent class
       val matching = parents.firstOrNull { clazz ->
            clazz.methods.any {
                it.name == srcName && it.desc == srcDesc
            }
        }
       if (matching != null) {
           return false
       }
        return super.visitMethod(srcName, srcDesc)
    }
}