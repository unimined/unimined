package com.example.fabric;

import net.fabricmc.api.ModInitializer;

import java.util.logging.Logger;

public class ExampleModFabric implements ModInitializer {
	public static Logger LOGGER = Logger.getLogger("ExampleMod");

	@Override
	public void onInitialize() {
		LOGGER.info("Hello from Fabric!");
	}
}
