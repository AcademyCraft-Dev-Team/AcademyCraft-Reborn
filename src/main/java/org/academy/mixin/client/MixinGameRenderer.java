package org.academy.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.animation.AnimationManager;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    /**
     * For ResizeDisplayEvent
     */
    @Inject(method = "resize",at = @At("TAIL"))
    private void resize(int width, int height, CallbackInfo ci) {
        var event = new ResizeDisplayEvent(width, height);
        NeoForge.EVENT_BUS.post(event);
    }

    @Inject(method = "render",at = @At("HEAD"))
    private void onFrameUpdate(CallbackInfo ci) {
        AnimationManager.onFrameUpdate();
    }
}