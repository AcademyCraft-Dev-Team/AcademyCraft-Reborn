package org.academy.mixin.common;

import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.mentalout.control.DirectMobMovementAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Ghast.class)
public abstract class MixinGhast implements DirectMobMovementAccess {
    @Override
    public void academy$moveDirectly(Vec3 destination, double speedModifier) {
        var ghast = (Ghast) (Object) this;
        ghast.getMoveControl().setWantedPosition(
                ghast.getX(), ghast.getY(), ghast.getZ(), 0.0);
        var delta = destination.subtract(ghast.position());
        if (delta.lengthSqr() <= 1.0E-6) return;
        ghast.setDeltaMovement(delta.normalize().scale(0.14 * speedModifier));
    }

    @Override
    public void academy$stopDirectMovement() {
        var ghast = (Ghast) (Object) this;
        ghast.getMoveControl().setWantedPosition(ghast.getX(), ghast.getY(), ghast.getZ(), 0.0);
        ghast.setDeltaMovement(ghast.getDeltaMovement().scale(0.2));
    }
}
