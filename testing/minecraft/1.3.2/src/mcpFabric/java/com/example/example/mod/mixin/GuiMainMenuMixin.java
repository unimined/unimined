package com.example.example.mod.mixin;

import com.example.example.mod.*;
import net.minecraft.src.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(GuiMainMenu.class)
public class GuiMainMenuMixin {
	@Inject(method = "initGui", at = @At("HEAD"))
	private void onInit(CallbackInfo ci) {
		ExampleMod.LOGGER.info("This is the main menu!");
	}
}
