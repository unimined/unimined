package net.fabricmc.mappingio.format

import net.fabricmc.mappingio.MappingFlag
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.MappingVisitor
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.forEachInZip
import xyz.wagyourtail.unimined.util.openZipFileSystem
import xyz.wagyourtail.unimined.util.readZipInputStreamFor
import java.io.IOException
import java.lang.IllegalArgumentException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.extension
import kotlin.io.path.inputStream

object BytecodeToMappings {

    fun readFile(path: Path, visitor: MappingVisitor) {
        readFile(path, MappingUtil.NS_SOURCE_FALLBACK, visitor)
    }

    fun readFile(path: Path, sourceNs: String, visitor: MappingVisitor) {
        val flags = visitor.flags
        var parentVisitor: MappingVisitor? = null

        path.openZipFileSystem().use { fs ->

            val classMap = defaultedMapOf<String, ClassNode?> { srcName ->
                val cNode = ClassNode()
                try {
                    fs.getPath("$srcName.class").inputStream().use { ClassReader(it) }
                        .accept(cNode, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
                    cNode
                } catch (e: NoSuchFileException) {
                    null
                }
            }

            fun getParentNodes(classNode: ClassNode): Sequence<ClassNode> {
                var currentNode: ClassNode? = classNode
                return generateSequence {
                    val c = currentNode
                    currentNode = currentNode?.let { classMap[it.superName] }
                    c
                }.flatMap { node ->
                    node.interfaces
                        .mapNotNull { classMap[it] }
                        .flatMap { listOf(it) + getParentNodes(it) }
                }.drop(1)
            }

            @Suppress("NAME_SHADOWING")
            var visitor = visitor
            if (flags.contains(MappingFlag.NEEDS_UNIQUENESS) || flags.contains(MappingFlag.NEEDS_MULTIPLE_PASSES)) {
                parentVisitor = visitor
                visitor = MemoryMappingTree()
            }

            val visitHeader = visitor.visitHeader()

            if (visitHeader) {
                visitor.visitNamespaces(sourceNs, listOf())
            }

            if (visitor.visitContent()) {
                Files.walkFileTree(fs.getPath("/"), object : FileVisitor<Path> {
                    override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(s: Path, attrs: BasicFileAttributes?): FileVisitResult {
                        if (s.extension == "class") {
                            val cNode = ClassNode()
                            ClassReader(s.inputStream()).accept(cNode, 0)
                            if (visitor.visitClass(cNode.name)) {
                                for (method in cNode.methods) {
                                    // not a bridge
                                    if (method.access and Opcodes.ACC_BRIDGE != 0) {
                                        continue
                                    }

//                                    // check if bridge points to
//                                    if (cNode.methods.any {
//                                            it.access and Opcodes.ACC_BRIDGE != 0 &&
//                                                    it.instructions.any {
//                                                        it.opcode == Opcodes.INVOKEVIRTUAL &&
//                                                                it is MethodInsnNode &&
//                                                                it.owner == cNode.name &&
//                                                                it.name == method.name &&
//                                                                it.desc == method.desc
//                                                    }
//                                        }) {
//                                        continue
//                                    }

                                    // check if super type contains
                                    if (getParentNodes(cNode).any { clazz ->
                                            clazz.methods.any {
                                                it.name == method.name && it.desc == method.desc && it.access and Opcodes.ACC_PRIVATE == 0
                                            }
                                        }) {
                                        continue
                                    }

                                    visitor.visitMethod(method.name, method.desc)
                                }
                                cNode.fields.forEach { field ->
                                    visitor.visitField(field.name, field.desc)
                                }
                            }
                            if (classMap[cNode.name] != null) {
                                // strip into a copy
                                val copy = ClassNode()
                                for (method in cNode.methods) {
                                    if (method.instructions.size() > 0) {
                                        copy.visitMethod(
                                            method.access,
                                            method.name,
                                            method.desc,
                                            method.signature,
                                            method.exceptions.toTypedArray()
                                        )
                                    }
                                }
                                copy.interfaces = cNode.interfaces
                                copy.superName = cNode.superName
                                copy.name = cNode.name
                                classMap[cNode.name] = copy
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                        return FileVisitResult.CONTINUE
                    }

                })
            }
            visitor.visitEnd()

            if (parentVisitor != null) {
                (visitor as MappingTree).accept(parentVisitor)
            }
        }
    }

}