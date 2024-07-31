package com.example.example.mod.mixin;

import com.example.*;
import net.minecraft.src.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public class ExampleMixin {
	@Inject(method = "initGui", at = @At("HEAD"))
	private void onInit(CallbackInfo ci) {
		ExampleMod.LOGGER.info("This is the main menu!");
	}
}
