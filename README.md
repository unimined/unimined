# Unimined

unified minecraft modding environment.

## TODO for initial release
* remap fg2+ era at's back to notch (fixing mc 1.7+)
* remap user at's to notch
* auto disable combined on <=1.2.5
* figure out, why modloader not launching in dev due to classpath path instead of jar path crash
* reverify versions work

## TODO
* FG3+ support
* fix fg3 versions of 1.12.2
* fabric aw support
* combined jar support
* figure out how to get forge to recognise resources as part of the dev mod
* split fg2+ out of the mc jar
* figure out how to do automated testing
  * figure out how to determine the correctness of remap output

## Example Usage
```groovy
plugins {
    id 'java'
    id 'xyz.wagyourtail.unimined'
}

group 'com.example'
version '1.0-SNAPSHOT'

repositories {
    mavenCentral()
}

unimined {
    // debug, puts some things in build/unimined instead of ~/.gradle/caches/unimined
    useGlobalCache = false
}

minecraft {
    // current available options are: forge, jarMod, fabric
    // if you don't include this, it will default to no mod loader transforms
    forge {
        // required for 1.7+
        it.mcpVersion = '39'
        it.mcpChannel = 'stable'
    }
    // required when using mcp mappings
    mcRemapper.fallbackTarget = "searge"

    mcRemapper.tinyRemapperConf = {
        // most mcp mappings (except older format) dont include field desc
        it.ignoreFieldDesc(true)
        // this also fixes some issues with them, as it tells tiny remapper to try harder to resolve conflicts
        it.ignoreConflicts(true)
    }
}

mappings {
    // currently no options here
}

sourceSets {
    // enable the client,
    // split source sets only rn, tho main is shared
    client
}

dependencies {
    minecraft 'net.minecraft:minecraft:1.12.2'
    
    // this version is actually broken btw, on the post initial release roadmap to fix
    forge 'net.minecraftforge:forge:1.12.2-14.23.5.2860'
    
    // mappings "mappinggroup:mappingname:version"
}
```