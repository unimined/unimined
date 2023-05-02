package xyz.wagyourtail.unimined.api.minecraft

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class MinecraftResolver {
    abstract val version: String
}