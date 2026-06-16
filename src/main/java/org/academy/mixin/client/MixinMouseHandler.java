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
    private void onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        InputSystem.handleMouseScroll(xoffset, yoffset, ci);
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        InputSystem.handleMouseButton(rawButtonInfo.button(), action, rawButtonInfo.modifiers(), ci);
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void onMove(long handle, double xpos, double ypos, CallbackInfo ci) {
        if (handle == Minecraft.getInstance().getWindow().handle()) {
            InputSystem.handleMouseMove(xpos, ypos, ci);
        }
    }
}