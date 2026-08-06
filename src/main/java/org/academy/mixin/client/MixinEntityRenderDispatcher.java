package org.academy.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void academy$hideMentalPerceptionTarget(
            E entity,
            Frustum culler,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (MentalIntrusionClientState.isHidden(entity)) cir.setReturnValue(false);
    }
}
