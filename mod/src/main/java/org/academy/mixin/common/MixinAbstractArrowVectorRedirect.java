package org.academy.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileInterceptionService;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileRedirects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class MixinAbstractArrowVectorRedirect {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void academy$allowOriginalOwnerPickup(Player player, CallbackInfo ci) {
        var arrow = (AbstractArrow) (Object) this;
        var accessor = (AbstractArrowVectorAccessor) arrow;
        if (arrow.level().isClientSide()
                || !(accessor.academy$isInGround() || arrow.isNoPhysics())
                || arrow.shakeTime > 0
                || !VectorProjectileRedirects.allowsOriginalOwnerPickup(arrow, player)) {
            return;
        }
        if (accessor.academy$tryPickup(player)) {
            player.take(arrow, 1);
            arrow.discard();
        }
        ci.cancel();
    }

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
