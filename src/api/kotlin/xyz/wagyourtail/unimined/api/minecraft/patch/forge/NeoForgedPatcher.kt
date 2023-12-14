package xyz.wagyourtail.unimined.api.minecraft.patch.forge

import xyz.wagyourtail.unimined.api.minecraft.patch.jarmod.JarModPatcher

interface NeoForgedPatcher<T: JarModPatcher> : ForgeLikePatcher<T> {
}