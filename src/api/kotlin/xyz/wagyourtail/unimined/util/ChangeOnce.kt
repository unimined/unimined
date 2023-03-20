package xyz.wagyourtail.unimined.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ChangeOnce<T>(private val defaultValue: T): ReadWriteProperty<Any, T> {
    private var value: T = defaultValue

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return value
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        if (this.value != defaultValue) {
            throw IllegalStateException("Value is initialized")
        }
        this.value = value
    }
}