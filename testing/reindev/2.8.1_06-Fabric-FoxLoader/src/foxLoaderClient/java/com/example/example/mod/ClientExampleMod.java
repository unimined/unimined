package com.example.example.mod;

import com.fox2code.foxloader.loader.ClientMod;
import com.fox2code.foxloader.loader.Mod;

public class ClientExampleMod extends Mod implements ClientMod {
	@Override
	public void onInit() {
		ExampleMod.LOGGER.info("Hello FoxLoader client world!");
	}
}
