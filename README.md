# Unimined

unified minecraft modding environment.

## TODO for initial release

* ~~remap fg2+ era at's back to notch (fixing mc 1.7+)~~
* ~~remap user at's to notch~~
* ~~auto disable combined on <=1.2.5~~
* ~~figure out, why modloader not launching in dev due to classpath path instead of jar path~~

## TODO

* ~~Refactor, refactor, refactor~~
* ~~FG3+ support (>1.12.2)~~
* ~~test user AT support~~
* ~~fix fg3 versions of 1.12.2~~
* ~~fabric aw support~~
* ~~combined jar support : forge 1.13+ does this, do with the rest~~
* fix split jars on fg3
* ~~figure out how to get forge to recognise resources as part of the dev mod~~
* split fg2+ out of the mc jar
* figure out how to do automated testing
    * figure out how to determine the correctness of remap output
    * aka automate the verification that versions work
        * list of versions to verify
            * 1.19.2
            * 1.17.1
            * 1.16.5
            * 1.13.2
            * 1.12.2
            * 1.8.9
            * 1.7.10
            * 1.6.4
            * 1.5.2
            * 1.3.2
            * 1.2.5
            * 1.7.3
            * b1.3_01
            * a1.1.2_01
    * maybe by hash check?
* figure out what versions need `-Djava.util.Arrays.useLegacyMergeSort=true` to not randomly crash, this should really
  be part of the legacy mc version.json, or at least betacraft's, but it's not
* ~~make myself a maven to host this on~~: https://maven.wagyourtail.xyz
* ~~fix forge mappings on 1.17+~~
* ~~mixin support~~
* ~~add parchment mappings support~~

## Example Usage

```groovy
plugins {
    id 'java'
    id 'xyz.wagyourtail.unimined' // I'm using it from buildSrc, so I don't need a version, you probably do
}

group 'com.example'
version '1.0-SNAPSHOT'

repositories {
    mavenCentral()
}

// debug, puts some things in build/unimined instead of ~/.gradle/caches/unimined
unimined.useGlobalCache = false

// when targetting main the first arg is optional
// unimined.minecraft {
unimined.minecraft(sourceSets.main) {
    // defaults to combined on 1.3+ so you don't need to set this one
    side "combined"
    
    version "1.14.4"
    
    mappings {
        /* helper declarations - intermediary/searge are auto added by fabric/forge */
        
        // intermediary()
        // searge()
        mojmap()
        // retroMCP()
        // yarn(1)
        // parchment("1.19.3", "2022.12.18")
        // mcp("stable", "39+1.12")
        
        // these will auto-resolve with the first available from your declared mappings
        devNamespace "mojmap"
        devFallbackNamespace "intermediary"
    }
    
    // specify modloader
    /*
    fabric {
        accessWidener "src/main/resources/whatever.aw"
        loader "0.14.18"

        // set these ones to target fabric versions without intermediaries
        customIntermediaries = true
        prodNamespace "official"
        devMappings = null
    }
    */
    /*
    forge {
        forge "28.2.26"
        
        accessTransformer "src/main/resources/META-INF/accesstransformer.cfg"
        mixinConfig "modid.mixins.json"
    }
    */
    // other options available, see PatchProviders
    
    
    mods {
        // auto-genned, default to `configuration.${sourceSet.name}ModImplementation` (or just modImplementation if main),
        // these are additive, so always has at least this configuration
        remap(configurations.modImplementation) {
            // optional, these are auto-set
            namespace "intermediary"
            fallbackNamespace "intermediary"
            remapAtToLegacy = true // auto set to value in forge provider
            mixinRemap("unimined") // default value is none, this value does full mixin remapping for dev... may be necessary for run configs in some envs
            remapper {
                // tiny remapper settings
            }
        }
        // if you have multiple, and they have the same config, 
        // use a list of configurations so they can remap together for speed reasons
        // the configurations can be different, just the options in remap the same
        /*
        remap([configurations.a, configurations.b]) {
             // stuff
        }
        */
    }
    
    minecraftRemapper.config {
        // tiny remapper settings
    }
    
    
    // this is default value when sourceSet main...
    // would bind a remapJar task after jar
    remap(jar)
    
    // this one is custom
    remap(jar, "customRemap") {
        // these can be config'd here, but don't need to be. they can be configured below
    }
    
    runs {
        // off = true // disable runs
        config("client") {
            jvmArgs += ["-Dexample.arg=true"]
        }
    }
}

dependencies {
    // these get prepended with the sourceSet name for the mc config
    modImplementation "mod:identifier:stuff"
    include "mod:identifier:stuff"

    modImplementation "other:mod:stuff"
}

remapJar {
    // basically don't need to change anything here, but you can do things like set the classifier
}

customRemap {
    // extra remap after jar, can be used for second other-mapped output for
    // more complicated stuff
    prodNamespace "official"
}
```
