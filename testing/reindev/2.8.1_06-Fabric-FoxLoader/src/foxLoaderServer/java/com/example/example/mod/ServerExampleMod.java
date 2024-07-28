package com.example.example.mod;

import com.fox2code.foxloader.loader.Mod;
import com.fox2code.foxloader.loader.ServerMod;

public class ServerExampleMod extends Mod implements ServerMod {
	@Override
	public void onInit() {
		ExampleMod.LOGGER.info("Hello FoxLoader client world!");
	}
}
