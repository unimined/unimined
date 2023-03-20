package xyz.wagyourtail.unimined.minecraft.patch.forge.fg3.mcpconfig

import java.util.function.Function

sealed interface ConfigValue {
    companion object {
        val OUTPUT = "output"
        val PREVIOUS_OUTPUT_SUFFIX = "Output"
        val SRG_MAPPINGS_NAME = "mappings"

        fun of(str: String): ConfigValue {
            return if (str.startsWith("{") && str.endsWith("}")) {
                Variable(
                    str.substring(
                        1,
                        str.length - 1
                    )
                )
            } else Constant(str)
        }
    }

    fun <R> fold(
        constant: Function<in Constant, out R>,
        variable: Function<in Variable, out R>
    ): R

    data class Constant(val value: String): ConfigValue {
        override fun <R> fold(
            constant: Function<in Constant, out R>,
            variable: Function<in Variable, out R>
        ): R {
            return constant.apply(this)
        }
    }

    data class Variable(val name: String): ConfigValue {
        override fun <R> fold(
            constant: Function<in Constant, out R>,
            variable: Function<in Variable, out R>
        ): R {
            return variable.apply(this)
        }
    }
}