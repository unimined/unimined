package com.example.example.mod;

import net.fabricmc.api.ModInitializer;

import java.util.logging.Logger;

public class FabricExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		ExampleMod.LOGGER.info("Hello Fabric world!");
	}
}
