package com.example.fabric.client;

import com.example.example.mod.*;
import net.fabricmc.api.*;

public class ClientExampleMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ExampleMod.LOGGER.info("Hello Fabric client world!");
	}
}
