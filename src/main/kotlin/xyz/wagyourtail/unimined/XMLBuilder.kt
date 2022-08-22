package xyz.wagyourtail.unimined

import java.util.*

@Suppress("UNUSED")
class XMLBuilder {
    private val options: MutableMap<String, String?> = HashMap()
    private val children: MutableList<Any?> = LinkedList()
    private val type: String
    var inline: Boolean
    var startNewLine: Boolean

    constructor(type: String) {
        this.type = type
        inline = false
        startNewLine = true
    }

    constructor(type: String, inline: Boolean) {
        this.type = type
        this.inline = inline
        startNewLine = !inline
    }

    constructor(type: String, inline: Boolean, startNewLine: Boolean) {
        this.type = type
        this.inline = inline
        this.startNewLine = startNewLine
    }

    fun addOption(key: String, option: String?): XMLBuilder {
        options[key] = option
        return this
    }

    fun addKeyOption(option: String): XMLBuilder {
        options[option] = null
        return this
    }

    fun setId(id: String): XMLBuilder {
        return addStringOption("id", id)
    }

    fun setClass(clazz: String): XMLBuilder {
        return addStringOption("class", clazz)
    }

    fun addStringOption(key: String, option: String): XMLBuilder {
        options[key] = "\"" + option.replace("\"", "&quot;") + "\""
        return this
    }

    fun append(vararg children: Any?): XMLBuilder {
        this.children.addAll(listOf(*children))
        return this
    }

    fun pop(index: Int): Any? {
        return children.removeAt(index)
    }

    fun pop(): Any? {
        return children.removeAt(children.size - 1)
    }

    override fun toString(): String {
        val builder = StringBuilder("<").append(type)
        options.forEach { (key, value) ->
            builder.append(" ").append(key)
            if (value != null) builder.append("=").append(value)
        }
        builder.append(">")
        if (children.isEmpty()) {
            builder.append("</").append(type).append(">")
        } else {
            var inline = inline
            for (rawChild in children) {
                if (rawChild is XMLBuilder) {
                    builder.append(if ((inline || rawChild.inline) && !rawChild.startNewLine) "" else "\n    ")
                        .append(tabIn(rawChild.toString(), rawChild.inline))
                    inline = rawChild.inline
                } else if (rawChild != null) {
                    builder.append(if (inline) "" else "\n    ").append(tabIn(rawChild.toString(), inline))
                    inline = this.inline
                }
            }
            builder.append(if (this.inline) "" else "\n").append("</").append(type).append(">")
        }
        return builder.toString()
    }

    private fun tabIn(string: String, inline: Boolean): String {
        return if (inline) string else string.replace("\n".toRegex(), "\n    ")
    }
}
