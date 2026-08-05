package org.academy.mixin.common;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.academy.internal.common.ability.accelerator.skills.lv1.KineticEnergyApplied;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;

@Mixin(Projectile.class)
public abstract class MixinProjectile {
    @Inject(method = "tick", at = @At("HEAD"))
    private void academy$reflectNearProtectedPlayer(CallbackInfo ci) {
        var projectile = (Projectile) (Object) this;
        if (projectile.level().isClientSide()
                || projectile.getData(AttachmentTypes
                .VECTOR_REFLECTED_PROJECTILE.get())) return;
        var velocity = projectile.getDeltaMovement();
        var path = projectile.getBoundingBox()
                .minmax(projectile.getBoundingBox().move(velocity))
                .inflate(1.5);
        var owner = projectile.getOwner();
        var closest = projectile.level().getEntitiesOfClass(Player.class, path, candidate ->
                        candidate instanceof ServerPlayer player
                                && candidate != owner
                                && VectorReflection.Server.shouldReflectProjectileFor(player, projectile))
                .stream()
                .min(Comparator.comparingDouble(projectile::distanceToSqr))
                .orElse(null);
        if (closest instanceof ServerPlayer player) {
            VectorReflection.Server.reflectProjectile(player, projectile);
        }
    }

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void academy$reflectProtectedPlayerHit(HitResult result, CallbackInfo ci) {
        var projectile = (Projectile) (Object) this;
        if (projectile.level().isClientSide() || !(result instanceof EntityHitResult entityHit)) return;
        if (entityHit.getEntity() instanceof ServerPlayer player
                && VectorReflection.Server.reflectProjectile(player, projectile)) {
            ci.cancel();
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

        var f = -Mth.sin(yRot * ((float) Math.PI / 180F)) * Mth.cos(xRot * ((float) Math.PI / 180F));
        var f1 = -Mth.sin((xRot + yOffset) * ((float) Math.PI / 180F));
        var f2 = Mth.cos(yRot * ((float) Math.PI / 180F)) * Mth.cos(xRot * ((float) Math.PI / 180F));

        projectile.shoot(f, f1, f2, pow, uncertainty);

        var vec3 = source.getDeltaMovement();
        projectile.setDeltaMovement(projectile.getDeltaMovement().add(vec3.x, source.onGround() ? 0.0D : vec3.y, vec3.z));
        ci.cancel();
    }
}
