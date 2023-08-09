package xyz.wagyourtail.unimined.internal.mapping.extension

fun splitNameAndDescriptor(nameAndDescriptor: String): Pair<String, String?> {
    val mName = nameAndDescriptor.substringBefore("(").substringAfter(";")
    val desc = if (nameAndDescriptor.contains("(")) "(${nameAndDescriptor.substringAfter("(")}" else null
    return Pair(mName, desc)
}