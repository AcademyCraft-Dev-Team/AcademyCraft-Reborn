package org.academy.mixin.common;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.internal.common.ability.accelerator.skills.lv1.KineticEnergyApplied;
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