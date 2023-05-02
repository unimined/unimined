package xyz.wagyourtail.unimined.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class FinalizeOnRead<T>(var value: T): ReadWriteProperty<Any?, T> {

    private var finalized = false

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        finalized = true
        return value
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (finalized) {
            throw IllegalStateException("Value is finalized")
        }
        this.value = value
    }


}
