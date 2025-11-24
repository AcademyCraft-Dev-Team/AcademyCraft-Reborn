package org.academy.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.api.client.gui.imgui.ImGuiUtilApi;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jspecify.annotations.Nullable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow
    @Nullable
    public Screen screen;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void runTick(CallbackInfo info) {
        NeoForge.EVENT_BUS.post(new MainLoopEvent());
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(CallbackInfo ci) {
        ImGuiUtilApi.init();
        AcademyCraftClient.init();
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void setLevel(ClientLevel clientLevel, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        var width = mainRenderTarget.width;
        var height = mainRenderTarget.height;
        AcademyCraftClient.resize(width, height);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        ImGuiUtilApi.close();
    }
}