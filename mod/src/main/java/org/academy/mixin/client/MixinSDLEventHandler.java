package org.academy.mixin.client;

import com.mojang.blaze3d.platform.SDLEventHandler;
import org.academy.internal.client.gui.imgui.ImGuiUtilInternal;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.3 使用 SDL3 事件泵。这里把 MC 实际消费的 SDL 事件按原样转发给 ImGui 的 SDL 输入后端，
 * 保证 ImGui 收到的输入与游戏一致（不会收到被 {@code flushInputEvents} 丢弃的陈旧事件）。
 */
@Mixin(SDLEventHandler.class)
public abstract class MixinSDLEventHandler {
    @Inject(method = "handleKeyEvent", at = @At("HEAD"))
    private void academy$imguiKeyEvent(SDL_Event event, CallbackInfo ci) {
        ImGuiUtilInternal.INSTANCE.onSdlEvent(event);
    }

    @Inject(method = "handleTextInputEvent", at = @At("HEAD"))
    private void academy$imguiTextInputEvent(SDL_Event event, CallbackInfo ci) {
        ImGuiUtilInternal.INSTANCE.onSdlEvent(event);
    }

    @Inject(method = "handleMouseMotionEvent", at = @At("HEAD"))
    private void academy$imguiMouseMotionEvent(SDL_Event event, CallbackInfo ci) {
        ImGuiUtilInternal.INSTANCE.onSdlEvent(event);
    }

    @Inject(method = "handleMouseButtonEvent", at = @At("HEAD"))
    private void academy$imguiMouseButtonEvent(SDL_Event event, CallbackInfo ci) {
        ImGuiUtilInternal.INSTANCE.onSdlEvent(event);
    }

    @Inject(method = "handleMouseWheelEvent", at = @At("HEAD"))
    private void academy$imguiMouseWheelEvent(SDL_Event event, CallbackInfo ci) {
        ImGuiUtilInternal.INSTANCE.onSdlEvent(event);
    }
}
