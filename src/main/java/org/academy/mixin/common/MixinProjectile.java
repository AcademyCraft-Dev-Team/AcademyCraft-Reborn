package org.academy.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorAttackAttributionResolver;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileInterceptionService;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileRedirects;
import org.academy.internal.common.ability.accelerator.reflection.compat.VectorProjectileTargeting;
import org.academy.internal.common.ability.accelerator.skills.lv2.KineticEnergyApplied;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;

@Mixin(Projectile.class)
public abstract class MixinProjectile {
    @Inject(method = "tick", at = @At("HEAD"))
    private void academy$reflectNearProtectedPlayer(CallbackInfo ci) {
        var projectile = (Projectile) (Object) this;
        if (projectile.level().isClientSide()) return;
        VectorAttackAttributionResolver.captureProjectileOwner(projectile);
        if (VectorProjectileRedirects.isRedirected(projectile)) {
            VectorProjectileTargeting.maintainRedirectTarget(projectile);
            return;
        }
        var velocity = projectile.getDeltaMovement();
        var path = projectile.getBoundingBox()
                .minmax(projectile.getBoundingBox().move(velocity))
                .inflate(1.5);
        var owner = projectile.getOwner();
        var closest = projectile.level().getEntitiesOfClass(Player.class, path, candidate ->
                        candidate instanceof ServerPlayer player
                                && candidate != owner
                                && VectorProjectileInterceptionService.canIntercept(player, projectile))
                .stream()
                .min(Comparator.comparingDouble(projectile::distanceToSqr))
                .orElse(null);
        if (closest instanceof ServerPlayer player) {
            VectorProjectileInterceptionService.intercept(player, projectile);
        }
    }

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void academy$reflectProtectedPlayerHit(HitResult result, CallbackInfo ci) {
        var projectile = (Projectile) (Object) this;
        if (projectile.level().isClientSide() || !(result instanceof EntityHitResult entityHit)) return;
        if (VectorProjectileTargeting.blocksVectorDefenderHit(
                projectile, entityHit.getEntity())) {
            VectorProjectileTargeting.maintainRedirectTarget(projectile);
            ci.cancel();
            return;
        }
        if (entityHit.getEntity() instanceof ServerPlayer player
                && VectorProjectileInterceptionService.intercept(player, projectile)) {
            ci.cancel();
        }
    }

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void academy$blockRedirectedVectorDefenderHit(
            Entity candidate,
            CallbackInfoReturnable<Boolean> cir
    ) {
        var projectile = (Projectile) (Object) this;
        if (VectorProjectileTargeting.blocksVectorDefenderHit(projectile, candidate)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "shootFromRotation",
            at = @At("HEAD"),
            cancellable = true
    )
    private void shootFromRotation(
            Entity source,
            float xRot,
            float yRot,
            float yOffset,
            float pow,
            float uncertainty,
            CallbackInfo ci
    ) {
        var projectile = (Projectile) (Object) this;
        if (projectile.level().isClientSide()) return;
        pow = KineticEnergyApplied.Server.onProjectileShoot(projectile, source, pow);

        var f = -Mth.sin(yRot * (Mth.PI / 180F)) * Mth.cos(xRot * (Mth.PI / 180F));
        var f1 = -Mth.sin((xRot + yOffset) * (Mth.PI / 180F));
        var f2 = Mth.cos(yRot * (Mth.PI / 180F)) * Mth.cos(xRot * (Mth.PI / 180F));

        projectile.shoot(f, f1, f2, pow, uncertainty);

        var vec3 = source.getDeltaMovement();
        projectile.setDeltaMovement(projectile.getDeltaMovement().add(vec3.x, source.onGround() ? 0.0D : vec3.y, vec3.z));
        ci.cancel();
    }
}
