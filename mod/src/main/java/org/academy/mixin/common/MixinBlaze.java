package org.academy.mixin.common;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.mentalout.control.DirectMobMovementAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Blaze.class)
public abstract class MixinBlaze implements DirectMobMovementAccess {
    @Override
    public void academy$moveDirectly(Vec3 destination, double speedModifier) {
        var blaze = (Blaze) (Object) this;
        blaze.getNavigation().stop();
        blaze.getMoveControl().setWantedPosition(
                destination.x,
                destination.y,
                destination.z,
                speedModifier
        );
        var movement = blaze.getDeltaMovement();
        var delta = destination.subtract(blaze.position());
        if (delta.lengthSqr() > 1.0E-6) {
            var desired = delta.normalize().scale(0.16 * speedModifier);
            var verticalVelocity = Mth.clamp(desired.y, -0.15, 0.15);
            blaze.setDeltaMovement(
                    Mth.lerp(0.35, movement.x, desired.x),
                    verticalVelocity,
                    Mth.lerp(0.35, movement.z, desired.z)
            );
        }
        blaze.needsSync = true;
    }

    @Override
    public void academy$stopDirectMovement() {
        var blaze = (Blaze) (Object) this;
        blaze.getMoveControl().setWantedPosition(blaze.getX(), blaze.getY(), blaze.getZ(), 0.0);
        blaze.setDeltaMovement(blaze.getDeltaMovement().scale(0.25));
    }
}
