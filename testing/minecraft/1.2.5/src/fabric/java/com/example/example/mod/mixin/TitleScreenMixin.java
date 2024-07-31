package com.example.example.mod.mixin;

import com.example.example.mod.*;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
	@Inject(method = "init", at = @At("HEAD"))
	private void onInit(CallbackInfo ci) {
		ExampleMod.LOGGER.info("This is the main menu!");
	}
}
