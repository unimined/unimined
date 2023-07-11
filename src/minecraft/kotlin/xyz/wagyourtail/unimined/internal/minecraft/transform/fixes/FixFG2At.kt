package xyz.wagyourtail.unimined.internal.minecraft.transform.fixes

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.nio.file.FileSystem
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

object FixFG2At {

    // because for some reason, forge doesn't have a way to do this itself...
    fun fixForgeATs(fileSystem: FileSystem) {
        // net.minecraftforge.fml.common.asm.transformers.ModAccessTransformer
        val modAt = fileSystem.getPath("/net/minecraftforge/fml/common/asm/transformers/ModAccessTransformer.class")
        if (!modAt.exists()) return
        val classNode = ClassNode()
        val classReader = ClassReader(modAt.readBytes())
        classReader.accept(classNode, 0)

        // check embedded field exists
        val field = classNode.fields.firstOrNull { it.name == "embedded" } ?: return

        // insert into the end of clinit, or create if it doesn't exist
        var clinit = classNode.methods.firstOrNull { it.name == "<clinit>" }
        if (clinit == null) {
            clinit = classNode.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).apply {
                visitCode()
                visitInsn(Opcodes.RETURN)
                visitMaxs(0, 0)
                visitEnd()
            } as MethodNode
        }

        // create own function
        val insertFromClasspath = classNode.visitMethod(Opcodes.ACC_STATIC, "unimined\$insertFromClasspath", "()V", null, null).apply {
            visitCode()
            // embedded = com.google.common.collect.Maps.newHashMap()
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/google/common/collect/Maps",
                "newHashMap",
                "()Ljava/util/HashMap;",
                false
            )
            visitFieldInsn(
                Opcodes.PUTSTATIC,
                "net/minecraftforge/fml/common/asm/transformers/ModAccessTransformer",
                "embedded",
                "Ljava/util/Map;"
            )
            // var manifests = ModAccessTransform.class.getClassLoader().getResources("META-INF/MANIFEST.MF")
            visitLdcInsn(Type.getType("Lnet/minecraftforge/fml/common/asm/transformers/ModAccessTransformer;"))
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Class",
                "getClassLoader",
                "()Ljava/lang/ClassLoader;",
                false
            )
            visitLdcInsn("META-INF/MANIFEST.MF")
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/ClassLoader",
                "getResources",
                "(Ljava/lang/String;)Ljava/util/Enumeration;",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 0)
            // while (manifests.hasMoreElements()) {
            val loopStart = Label()
            visitLabel(loopStart)
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "java/util/Enumeration",
                "hasMoreElements",
                "()Z",
                true
            )
            val loopEnd = Label()
            visitJumpInsn(Opcodes.IFEQ, loopEnd)
            //     var manifest = manifests.nextElement()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "java/util/Enumeration",
                "nextElement",
                "()Ljava/lang/Object;",
                true
            )
            visitVarInsn(Opcodes.ASTORE, 1)
            //     var attributes = new Manifest(manifest.openStream()).getMainAttributes()
            visitTypeInsn(Opcodes.NEW, "java/util/jar/Manifest")
            visitInsn(Opcodes.DUP)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitTypeInsn(Opcodes.CHECKCAST, "java/net/URL")
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/net/URL",
                "openStream",
                "()Ljava/io/InputStream;",
                false
            )
            visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/util/jar/Manifest",
                "<init>",
                "(Ljava/io/InputStream;)V",
                false
            )
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/jar/Manifest",
                "getMainAttributes",
                "()Ljava/util/jar/Attributes;",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 2)
            //     var fmlat = attributes.getValue("FMLAT")
            visitVarInsn(Opcodes.ALOAD, 2)
            visitLdcInsn("FMLAT")
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/util/jar/Attributes",
                "getValue",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 3)
            //     if (fmlat != null) {
            visitVarInsn(Opcodes.ALOAD, 3)
            val ifNull = Label()
            visitJumpInsn(Opcodes.IFNULL, ifNull)
            //         var lines = fmlat.split(" ")
            visitVarInsn(Opcodes.ALOAD, 3)
            visitLdcInsn(" ")
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "split",
                "(Ljava/lang/String;)[Ljava/lang/String;",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 4)
            //         for (int i = 0; i < lines.length; i++) {
            val forStart = Label()
            visitInsn(Opcodes.ICONST_0)
            visitVarInsn(Opcodes.ISTORE, 5)
            visitLabel(forStart)
            val forEnd = Label()
            visitVarInsn(Opcodes.ILOAD, 5)
            visitVarInsn(Opcodes.ALOAD, 4)
            visitInsn(Opcodes.ARRAYLENGTH)
            visitJumpInsn(Opcodes.IF_ICMPGE, forEnd)
            //             var line = lines[i]
            visitVarInsn(Opcodes.ALOAD, 4)
            visitVarInsn(Opcodes.ILOAD, 5)
            visitInsn(Opcodes.AALOAD)
            visitVarInsn(Opcodes.ASTORE, 6)
            //             var atFile = ModAccessTransformer.class.getClassLoader().getResources("META-INF/" + line)
            visitLdcInsn(Type.getType("Lnet/minecraftforge/fml/common/asm/transformers/ModAccessTransformer;"))
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Class",
                "getClassLoader",
                "()Ljava/lang/ClassLoader;",
                false
            )
            visitLdcInsn("META-INF/")
            visitVarInsn(Opcodes.ALOAD, 6)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/ClassLoader",
                "getResources",
                "(Ljava/lang/String;)Ljava/util/Enumeration;",
                false
            )
            visitVarInsn(Opcodes.ASTORE, 7)
            //             while (atFile.hasMoreElements()) {
            val whileStart = Label()
            visitLabel(whileStart)
            visitVarInsn(Opcodes.ALOAD, 7)
            visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "java/util/Enumeration",
                "hasMoreElements",
                "()Z",
                true
            )
            val whileEnd = Label()
            visitJumpInsn(Opcodes.IFEQ, whileEnd)
            //                 var at = atFile.nextElement()
            visitVarInsn(Opcodes.ALOAD, 7)
            visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "java/util/Enumeration",
                "nextElement",
                "()Ljava/lang/Object;",
                true
            )
            visitTypeInsn(Opcodes.CHECKCAST, "java/net/URL")
            visitVarInsn(Opcodes.ASTORE, 8)
            //                embedded.put(at.toString(), IOUtils.toString(at.openStream()))
            visitFieldInsn(
                Opcodes.GETSTATIC,
                "net/minecraftforge/fml/common/asm/transformers/ModAccessTransformer",
                "embedded",
                "Ljava/util/Map;"
            )
            visitVarInsn(Opcodes.ALOAD, 8)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/net/URL",
                "toString",
                "()Ljava/lang/String;",
                false
            )
            visitVarInsn(Opcodes.ALOAD, 8)
            visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/net/URL",
                "openStream",
                "()Ljava/io/InputStream;",
                false
            )
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/apache/commons/io/IOUtils",
                "toString",
                "(Ljava/io/InputStream;)Ljava/lang/String;",
                false
            )
            visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "java/util/Map",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                true
            )
            visitInsn(Opcodes.POP)
            //             }
            visitJumpInsn(Opcodes.GOTO, whileStart)
            visitLabel(whileEnd)
            //         }
            visitIincInsn(5, 1)
            visitJumpInsn(Opcodes.GOTO, forStart)
            visitLabel(forEnd)
            //     }
            visitLabel(ifNull)
            // }
            visitJumpInsn(Opcodes.GOTO, loopStart)
            visitLabel(loopEnd)
            visitInsn(Opcodes.RETURN)
            visitEnd()
        }

        clinit.instructions.first { it.opcode == Opcodes.RETURN }.apply {
            clinit.instructions.insertBefore(this, MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraftforge/fml/common/asm/transformers/ModAccessTransformer", "unimined\$insertFromClasspath", "()V", false))
        }

        val classWriter = ModLoaderPatches.ClassWriterASM(fileSystem)
        classNode.accept(classWriter)
        modAt.writeBytes(classWriter.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
    }
}