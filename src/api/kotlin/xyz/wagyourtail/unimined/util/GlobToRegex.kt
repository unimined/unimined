package xyz.wagyourtail.unimined.util

object GlobToRegex {

    private val globToRegex = mapOf(
        "**/" to "(?:.*\\/)?",
        "**" to ".*",
        "*" to "\\/?[^/]*",
        "?" to ".",
        "[" to "\\[",
        "]" to "\\]",
        "^" to "\\^",
        "$" to "\\$",
        "/./" to "\\/",
        "." to "\\.",
        "(" to "\\(",
        ")" to "\\)",
        "|" to "\\|",
        "+" to "\\+",
        "{" to "\\{",
        "}" to "\\}",
    )

    private val any = Regex(".")

    private fun escapeChars(inp: String): String {
        return inp.replace(any, "\\\\$0")
    }

    // match keys, skip if already escaped
    val patternMatcher = "(?<!\\\\)(?:${globToRegex.keys.joinToString("|") { escapeChars(it) }})".toRegex()

    fun apply(glob: String): String {
        if (glob.startsWith("./")) {
            return apply(glob.substring(2))
        }
        return "^" + patternMatcher.replace(glob) {
            globToRegex[it.value]!!
        } + "$"
    }
}

fun String.globToRegex() = Regex(GlobToRegex.apply(this))