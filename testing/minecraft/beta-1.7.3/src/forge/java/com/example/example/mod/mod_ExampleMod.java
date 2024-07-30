package com.example.example.mod;

import com.example.ExampleMod;
import net.minecraft.src.*;

import java.util.*;

public class mod_ExampleMod extends BaseMod {
    private final Properties properties = new Properties();

    {
        try {
            properties.load(mod_ExampleMod.class.getResourceAsStream("/exampleMod.properties"));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String Version() {
        return properties.getProperty("version");
    }

    @Override
    public void ModsLoaded() {
        ExampleMod.LOGGER.info("Hello client ModLoader world!");
    }
}
