# Multi-SourceSet/Project

## Multi-SourceSets

If you have multiple sourceSets that are seperate mods, but with the same minecraft configurations:
```gradle
unimined.minecraft([sourceSets.a, sourceSets.b]) {
    ...
}
```

for the same mod, but with multiple sourcesets
```gradle
unimined.minecraft {
...
}

unimined.minecraft(sourceSets.a) {
    combineWith(sourceSets.main)
}
```

## Multi-Loader

this is an extension of the multi-sourcesets concept, but with different modloaders.
```gradle
// main as common
unimined.minecraft {
    mappings {
       mojmap()
    }
    
    accessWidener {
        accessWidener "src/main/resources/accessWidenerName.aw"
    }
}

// forge
unimined.minecraft(sourceSets.forge) {
    combineWith(sourceSets.main)
    minecraftForge {
        loader project.forgeVersion
        accessTransformer aw2at("src/main/resources/accessWidenerName.aw")
    }
}

// fabric
unimined.minecraft(sourceSets.fabric) {
    combineWith(sourceSets.main)
    fabric {
        loader project.fabricLoaderVersion
        accessWidener "src/main/resources/accessWidenerName.aw"
    }
}
```

## Multi-Project

multi-project is similar to multi-sourceSet, but with different projects.
this is useful for porting arch-loom projects. for new projects, it is recommended to not use this.

### main build gradle
```gradle
...
subprojects {
    ...
    unimined.minecraft(sourceSets.main, true) { // the true here defer's loading until the next time unimined.minecraft is called, (in each subproject's build.gradle)
        mappings {
           mojmap()
           
           devFallbackNamespace "official" // ensure the same fallback namespace
        }
    }
}

```
### common build.gradle
```gradle
unimined.minecraft {
    accessWidener {
        accessWidener "src/main/resources/accessWidenerName.aw"
    }
}
```

### fabric build.gradle
```gradle
unimined.minecraft {
    combineWith(":common:main") // combine with common, for identifying both together as one mod for dev runs 
    fabric {
        loader project.fabricLoaderVersion
        accessWidener "../common/src/main/resources/accessWidenerName.aw"
    }
}
```

### forge build.gradle
```gradle
unimined.minecraft {
    combineWith(":common:main") // combine with common, for identifying both together as one mod for dev runs 
    minecraftForge {
        loader project.forgeVersion
        accessTransformer aw2at("../common/src/main/resources/accessWidenerName.aw")
    }
}
```