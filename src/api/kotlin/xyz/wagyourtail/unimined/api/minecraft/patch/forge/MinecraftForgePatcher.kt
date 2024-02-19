package xyz.wagyourtail.unimined.api.minecraft.patch.forge

import xyz.wagyourtail.unimined.api.minecraft.patch.jarmod.JarModPatcher

interface MinecraftForgePatcher<T: JarModPatcher> : ForgeLikePatcher<T> {

    var useUnionRelaunch: Boolean

}