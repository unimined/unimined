# Unimined

unified minecraft modding environment.

## Supported Loaders
* Fabric
* Quilt
* Forge
* Neoforge

## Planned Loaders
* Bukkit Derrivitives (at least paper)
* LiteLoader
* Sponge

## Custom Loaders
yes, this is possible, see [PrcraftExampleMod](https://github.com/prcraft-minecraft/PrcraftExampleMod) and it's buildsrc dir.

## TODO
* rework mcpconfig runner to be more kotlin and less old version of arch-loom code
* Fabric injected interfaces
* Forge JarJar

## Setup
1. take one of the versions from [testing](./testing)
1. remove `includeBuild('../../')` from `settings.gradle`
1. put a proper version number for the plugin in `build.grade`
