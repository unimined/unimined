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
        return "^" + patternMatcher.replace(glob) {
            globToRegex[it.value]!!
        } + "$"
    }
}

fun main(vararg args: String) {
    println(GlobToRegex.patternMatcher)

    // unit tests
    println(GlobToRegex.apply("*.java"))
    println(GlobToRegex.apply("**/*.java"))
    println(GlobToRegex.apply("*.js?"))
    println(GlobToRegex.apply("**/.gitignore"))
    println(GlobToRegex.apply("*.js{on,}"))
    println(GlobToRegex.apply("*.js\\*"))
}