package com.example.example.mod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "modid")
public class ExampleModForge {
    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        ExampleMod.LOGGER.info("Hello from Minecraft Forge / Cleanroom!");
    }
}
