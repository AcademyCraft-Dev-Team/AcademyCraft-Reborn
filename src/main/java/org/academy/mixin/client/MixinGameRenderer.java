package org.academy.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.academy.api.client.gui.animation.AnimationManager;
import org.academy.api.client.hud.HUDManager;
import org.academy.api.client.render.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Inject(method = "render", locals = LocalCapture.CAPTURE_FAILSOFT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    public void gui(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci, boolean flag, int i, int j, Window window, Matrix4f matrix4f, Matrix4fStack matrix4fstack, GuiGraphics guigraphics) {
        AnimationManager.getInstance().onFrameUpdate();
        var stack = new MatrixStack();
        stack.setFrom(guigraphics.pose().last());
        HUDManager.render(stack, guigraphics.bufferSource(), deltaTracker.getGameTimeDeltaPartialTick(true));
    }
}