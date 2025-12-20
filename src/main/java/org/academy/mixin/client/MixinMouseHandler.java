package org.academy.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.academy.api.client.input.InputSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * For InputSystem
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        InputSystem.handleMouseScroll(xOffset, yOffset, ci);
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onButton(long windowPointer, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        InputSystem.handleMouseButton(buttonInfo.button(), action, buttonInfo.modifiers(), ci);
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void onMove(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        if (windowPointer == Minecraft.getInstance().getWindow().handle()) {
            InputSystem.handleMouseMove(xOffset, yOffset, ci);
        }
    }
}