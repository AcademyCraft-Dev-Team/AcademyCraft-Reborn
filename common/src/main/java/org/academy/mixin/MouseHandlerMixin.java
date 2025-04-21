package org.academy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.academy.api.client.input.InputSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"))
    private void onScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        InputSystem.handleMouseScroll(windowPointer, xOffset, yOffset);
    }

    @Inject(method = "onPress", at = @At("HEAD"))
    private void onPress(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        InputSystem.handleMouseButton(button, action, modifiers);
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void onMove(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        if (windowPointer == Minecraft.getInstance().getWindow().getWindow()) {
            InputSystem.handleMouseMove(xOffset, yOffset);
        }
    }
}
