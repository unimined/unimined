package xyz.wagyourtail.unimined.refmap

import net.fabricmc.tinyremapper.TinyRemapper
import net.fabricmc.tinyremapper.api.TrClass
import net.fabricmc.tinyremapper.api.TrEnvironment
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension
import org.objectweb.asm.ClassVisitor
import java.nio.file.Path

class RefmapBuilder(val mixinJson: Set<Path>, val defaultMixinPath: Path) :
    MixinExtension(setOf(AnnotationTarget.HARD)),
        TinyRemapper.Extension,
        TinyRemapper.StateProcessor,
        TinyRemapper.ApplyVisitorProvider {

    override fun attach(builder: TinyRemapper.Builder) {
        super.attach(builder)

        builder.extraStateProcessor(this)
        builder.extraPreApplyVisitor(this)
    }

    override fun process(env: TrEnvironment) {
        TODO("Not yet implemented")
    }

    override fun insertApplyVisitor(cls: TrClass?, next: ClassVisitor?): ClassVisitor {
        TODO("Not yet implemented")
    }
}