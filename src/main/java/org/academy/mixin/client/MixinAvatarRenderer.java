package org.academy.mixin.client;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.academy.internal.client.renderer.entity.layers.SkillEffectsLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * For SkillEffectsLayer
 */
@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        var ins = (AvatarRenderer<?>) (Object) this;
        ins.addLayer(new SkillEffectsLayer(ins));
    }
}