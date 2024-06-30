package com.example;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "modid")
public class ExampleModForge {

    public ExampleModForge() {
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        ExampleMod.LOGGER.info("Hello from Forge!");
    }

}
