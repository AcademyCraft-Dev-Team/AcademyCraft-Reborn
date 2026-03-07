package org.academy.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow
    @Nullable
    public Screen screen;

    @Shadow
    @Final
    private Window window;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void runTick(CallbackInfo info) {
        NeoForge.EVENT_BUS.post(new MainLoopEvent());
    }

    /**
     * For ResizeDisplayEvent
     */
    @Inject(method = "resizeGui", at = @At("TAIL"))
    private void resize(CallbackInfo ci) {
        var event = new ResizeDisplayEvent(window.getWidth(), window.getHeight());
        NeoForge.EVENT_BUS.post(event);
    }
}