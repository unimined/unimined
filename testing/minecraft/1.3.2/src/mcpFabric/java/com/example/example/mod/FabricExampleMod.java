package com.example.example.mod;

import net.fabricmc.api.*;

public class FabricExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		ExampleMod.LOGGER.info("Hello Fabric world!");
	}
}
