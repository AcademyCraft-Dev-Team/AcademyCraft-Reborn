package org.academy.mixin;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.academy.internal.client.renderer.entity.layers.SkillEffectsLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer {
    @Unique
    public PlayerRenderer academyCraft$instance;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        academyCraft$instance = (PlayerRenderer) (Object) this;
        academyCraft$instance.addLayer(new SkillEffectsLayer(academyCraft$instance));
    }
}