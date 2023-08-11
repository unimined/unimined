package xyz.wagyourtail.unimined.internal.mapping.extension.jma

object JarModAgent {

    object AnnotationElement {

        const val NAME = "name"

    }

    object Annotation {

        const val CTRANSFORMER = "Lnet/lenni0451/classtransform/annotations/CTransformer;"
        const val CSHADOW = "Lnet/lenni0451/classtransform/annotations/CShadow;"

        const val CINJECT = "Lnet/lenni0451/classtransform/annotations/injection/CInject;"
        const val CMODIFYCONSTANT = "Lnet/lenni0451/classtransform/annotations/injection/CModifyConstant;"
        const val COVERRIDE = "Lnet/lenni0451/classtransform/annotations/injection/COverride;"
        const val CREDIRECT = "Lnet/lenni0451/classtransform/annotations/injection/CRedirect;"
        const val CWRAPCATCH = "Lnet/lenni0451/classtransform/annotations/injection/CWrapCatch;"
    }

}