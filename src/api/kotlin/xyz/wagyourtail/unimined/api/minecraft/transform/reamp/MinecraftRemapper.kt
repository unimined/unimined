package xyz.wagyourtail.unimined.api.minecraft.transform.reamp

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import net.fabricmc.tinyremapper.TinyRemapper
import xyz.wagyourtail.unimined.api.minecraft.transform.patch.ForgePatcher

abstract class MinecraftRemapper {

    abstract var tinyRemapperConf: (TinyRemapper.Builder) -> Unit

    fun setTinyRemapperConf(@DelegatesTo(
        value = ForgePatcher::class,
        strategy = Closure.DELEGATE_FIRST
    ) action: Closure<*>){
        tinyRemapperConf = {
            action.delegate = it
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

}