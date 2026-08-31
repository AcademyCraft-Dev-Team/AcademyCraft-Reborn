package org.academy.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.academy.internal.client.ability.aeromanip.HighSpeedJetHighlightClient;
import org.academy.internal.client.ability.mentalout.WideAreaInterferenceClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void academy$highlightHighSpeedJetSupport(
            T entity,
            S state,
            float partialTick,
            CallbackInfo ci
    ) {
        if (WideAreaInterferenceClientState.shouldOutline(entity)) {
            state.outlineColor = WideAreaInterferenceClientState.outlineColor(entity);
        } else if (HighSpeedJetHighlightClient.shouldHighlightEntity(entity)) {
            state.outlineColor = HighSpeedJetHighlightClient.WHITE_OUTLINE;
        }
    }
}
