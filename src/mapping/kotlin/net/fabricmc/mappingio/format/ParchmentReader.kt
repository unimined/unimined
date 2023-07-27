package net.fabricmc.mappingio.format

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingVisitor
import java.io.Reader

object ParchmentReader {

    fun read(reader: Reader, targetNs: String, visitor: MappingVisitor) {
        read(JsonParser.parseReader(reader).asJsonObject, targetNs, visitor)
    }

    fun read(json: JsonObject, targetNs: String, visitor: MappingVisitor) {

        if (visitor.visitHeader()) {
            visitor.visitNamespaces(targetNs, listOf(targetNs))
        }

        if (visitor.visitContent()) {

            // packages
//            json.get("packages")?.asJsonArray?.forEach {
//              // mappingio doesn't support package comments
//            }

            // classes
            json.get("classes")?.asJsonArray?.map { it.asJsonObject }?.forEach { clazz ->
                val name = clazz.get("name").asString

                if (visitor.visitClass(name)) {
                    val javadoc = clazz.get("javadoc")?.asJsonArray?.joinToString("\n") { it.asString }
                    if (javadoc != null) {
                        visitor.visitComment(MappedElementKind.CLASS, javadoc)
                    }

                    if (visitor.visitElementContent(MappedElementKind.CLASS)) {

                        // methods
                        clazz.get("methods")?.asJsonArray?.map { it.asJsonObject }?.forEach { method ->
                            val mname = method.get("name").asString
                            val descriptor = method.get("descriptor").asString

                            if (visitor.visitMethod(mname, descriptor)) {
                                val mjavadoc = method.get("javadoc")?.asJsonArray?.joinToString("\n") { it.asString }
                                if (mjavadoc != null) {
                                    visitor.visitComment(MappedElementKind.METHOD, mjavadoc)
                                }

                                if (visitor.visitElementContent(MappedElementKind.METHOD)) {
                                    method.get("parameters")?.asJsonArray?.map { it.asJsonObject }?.forEach { param ->
                                        val index = param.get("index").asInt
                                        val pname = param.get("name").asString

                                        visitor.visitMethodArg(-1, index, null)
                                        visitor.visitDstName(MappedElementKind.METHOD_ARG, 0, pname)
                                    }
                                }
                            }

                        }

                        // fields
                        clazz.get("fields")?.asJsonArray?.map { it.asJsonObject }?.forEach { field ->
                            val fname = field.get("name").asString
                            val descriptor = field.get("descriptor").asString

                            if (visitor.visitField(fname, descriptor)) {
                                val fjavadoc = field.get("javadoc")?.asJsonArray?.joinToString("\n") { it.asString }
                                if (fjavadoc != null) {
                                    visitor.visitComment(MappedElementKind.FIELD, fjavadoc)
                                }
                            }
                        }
                    }

                }
            }
        }

        visitor.visitEnd()
    }
}