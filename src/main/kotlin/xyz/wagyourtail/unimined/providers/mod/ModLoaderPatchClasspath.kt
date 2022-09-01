package xyz.wagyourtail.unimined.providers.mod

import org.objectweb.asm.*
import java.nio.file.FileSystem

object ModLoaderPatchClasspath {
    fun fixURIisNotHierarchicalException(fileSystem: FileSystem) {
        val modLoader = fileSystem.getPath("/ModLoader.class")
        val cr = ClassReader(modLoader.toFile().readBytes())
        val cw = object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                if (name == "init") {
                    // find the lines
                    //     File source = new File(ModLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    //     this.modDir.mkdirs();
                    //     readFromModFolder(this.modDir);
                    //     readFromClassPath(source);
                    // and replace them with
                    //     this.modDir.mkdirs();
                    //     readFromModFolder(this.modDir);
                    //     for (String path : System.getProperty("java.class.path").split(File.pathSeparator)) {
                    //         readFromClassPath(new File(path));
                    //     }


                }
            }
        }
    }
}