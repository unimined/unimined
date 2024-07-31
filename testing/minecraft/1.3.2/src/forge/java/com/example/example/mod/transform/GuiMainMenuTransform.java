package com.example.example.mod.transform;

import com.example.example.mod.*;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.src.GuiMainMenu;

@CTransformer(GuiMainMenu.class)
public class GuiMainMenuTransform {
    @CInject(method = "initGui", target = @CTarget("HEAD"))
    public void onInitGui(InjectionCallback callback) {
        ExampleMod.LOGGER.info("This is the main menu!");
    }
}
