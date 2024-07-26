package com.example.fabric.client;

import net.fabricmc.api.*;
import xyz.wagyourtail.unimined.example.mod.*;

public class ClientExampleMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ExampleMod.LOGGER.info("Hello Fabric client world!");
	}
}
