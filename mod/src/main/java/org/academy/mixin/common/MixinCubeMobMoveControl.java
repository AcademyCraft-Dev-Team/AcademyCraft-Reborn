package org.academy.mixin.common;

import org.academy.internal.common.ability.mentalout.control.CubeMobMoveControlAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob$CubeMobMoveControl")
public abstract class MixinCubeMobMoveControl implements CubeMobMoveControlAccess {
    @Shadow
    public abstract void setDirection(float yRot, boolean aggressive);

    @Shadow
    public abstract void setWantedMovement(double speedModifier);

    @Override
    public void academy$setMentalControlDirection(float yRot, boolean aggressive) {
        setDirection(yRot, aggressive);
    }

    @Override
    public void academy$setMentalControlMovement(double speedModifier) {
        setWantedMovement(speedModifier);
    }
}
