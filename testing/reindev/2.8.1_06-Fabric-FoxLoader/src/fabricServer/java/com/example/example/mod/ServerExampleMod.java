package com.example.example.mod;

import net.fabricmc.api.*;

public class ServerExampleMod implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		ExampleMod.LOGGER.info("Hello Fabric server world!");
	}
}
