# Unimined

unified minecraft modding environment with support for legacy environments.

## Supported Loaders
* Fabric
* Quilt
* Forge
* Neoforge
* Modloader
* [JarModAgent](https://github.com/unimined/JarModAgent)
* [Flint Loader](https://flintloader.net)
* just plain jarmodding
* Rift

## Planned Loaders
* Bukkit Derrivitives (at least paper)
* LiteLoader
* Sponge
* NilLoader

## Custom Loaders
yes, this is possible, see [PrcraftExampleMod](https://github.com/prcraft-minecraft/PrcraftExampleMod) and it's buildsrc dir.

## TODO
* stop using artifactural
* rework mcpconfig runner to be more kotlin and less old version of arch-loom code
* Forge JarJar
* Support for launch configs in other dev envs
  * vscode
  * eclipse
* support to login to minecraft in dev
* support to launch with the prod jar
* add datagen support
  * forge datagen
  * fabric datagen
  * quilt datagen
* fix yarn on neoforge (these will probably be agents, possibly in separate projects and pulled like JarModAgent)
  * inject remapper into ASMAPI
  * reflection remapper (also for forge potentially, or in general).
* genSources should apply forge source patches (at least on fg3+)

## Recommended Setup
1. take one of the versions from [testing](./testing)
1. remove `includeBuild('../../')` from `settings.gradle`
1. put a proper version number for the plugin in `build.grade`

## Other Setups

### Arch-Loom Style
* direct porting of arch-loom projects without changing the directory structure is possible.
* instructions pending...
### third party template(s)
* arch style: https://github.com/firstdarkdev/fdd-xplat
* //todo: add more
