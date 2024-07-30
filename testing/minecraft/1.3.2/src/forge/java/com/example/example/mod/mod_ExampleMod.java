package com.example.example.mod;

import net.minecraft.src.BaseMod;

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
    public String getVersion() {
        return properties.getProperty("version");
    }

	@Override
	public String getName() {
		return properties.getProperty("name");
	}

	@Override
    public void load() {
        ExampleMod.LOGGER.info("Hello Minecraft Forge / ModLoader world!");
    }
}
