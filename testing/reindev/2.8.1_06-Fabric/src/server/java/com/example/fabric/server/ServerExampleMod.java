package com.example.fabric.server;

import net.fabricmc.api.*;
import xyz.wagyourtail.unimined.example.mod.*;

public class ServerExampleMod implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		ExampleMod.LOGGER.info("Hello Fabric server world!");
	}
}
