package org.academy.mixin.common;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.internal.common.ability.accelerator.skills.KineticEnergyApplied;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class MixinProjectile {
    @Inject(
            method = "shootFromRotation",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy_replaceShootFromRotation(
            Entity shooter,
            float x,
            float y,
            float z,
            float velocity,
            float inaccuracy,
            CallbackInfo ci
    ) {
        var projectile = (Projectile) (Object) this;
        if (projectile.level().isClientSide()) return;
        if (KineticEnergyApplied.Server.SKILL_STATS.containsKey(shooter.getUUID()) && KineticEnergyApplied.Server.SKILL_STATS.get(shooter.getUUID())) {
            velocity = KineticEnergyApplied.Server.onProjectileShoot(projectile,shooter, velocity);

            float f = -Mth.sin(y * ((float) Math.PI / 180F)) * Mth.cos(x * ((float) Math.PI / 180F));
            float f1 = -Mth.sin((x + z) * ((float) Math.PI / 180F));
            float f2 = Mth.cos(y * ((float) Math.PI / 180F)) * Mth.cos(x * ((float) Math.PI / 180F));

            projectile.shoot(f, f1, f2, velocity, inaccuracy);

            var vec3 = shooter.getDeltaMovement();
            projectile.setDeltaMovement(projectile.getDeltaMovement().add(vec3.x, shooter.onGround() ? 0.0D : vec3.y, vec3.z));
            ci.cancel();
        }
    }
}