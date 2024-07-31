package com.example.example.mod.mixin;

import com.example.example.mod.*;
import net.minecraft.client.gui.screen.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
	@Inject(method = "init", at = @At("HEAD"))
	private void onInit(CallbackInfo ci) {
		ExampleMod.LOGGER.info("This is the main menu!");
	}
}
