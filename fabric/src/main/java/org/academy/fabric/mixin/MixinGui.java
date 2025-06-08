package org.academy.fabric.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.academy.api.client.hud.HUDManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {
    @Inject(method = "render", at = @At("HEAD"))
    private void render(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        HUDManager.render(guiGraphics, partialTick);
    }
}