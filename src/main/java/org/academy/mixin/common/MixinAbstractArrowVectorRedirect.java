package org.academy.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileInterceptionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class MixinAbstractArrowVectorRedirect {
    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void academy$redirectArrowAtImpact(EntityHitResult hitResult, CallbackInfo ci) {
        var arrow = (AbstractArrow) (Object) this;
        if (!arrow.level().isClientSide()
                && hitResult.getEntity() instanceof ServerPlayer player
                && VectorProjectileInterceptionService.intercept(player, arrow)) {
            ci.cancel();
        }
    }
}
