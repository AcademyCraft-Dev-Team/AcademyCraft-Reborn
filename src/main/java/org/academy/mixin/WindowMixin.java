package org.academy.mixin;

import com.mojang.blaze3d.platform.Window;
import org.academy.api.client.input.InputSystem;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {
    @Shadow
    @Final
    private long window;

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lcom/mojang/blaze3d/platform/Window;window:J", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void onInit(CallbackInfo ci) {
        InputSystem.window = window;
        InputSystem.scrollCallback = GLFW.glfwSetScrollCallback(window, (w, x, y) -> {
            InputSystem.accumulatedScrollDelta += y;
            InputSystem.scrollCallback.invoke(w, x, y);
        });
    }
}