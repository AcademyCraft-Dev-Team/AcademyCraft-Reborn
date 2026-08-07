package org.academy.mixin.common;

import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.mentalout.control.DirectMobMovementAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Phantom.class)
public abstract class MixinPhantom implements DirectMobMovementAccess {
    @Shadow
    private Vec3 moveTargetPoint;

    @Override
    public void academy$moveDirectly(Vec3 destination, double speedModifier) {
        moveTargetPoint = destination;
    }

    @Override
    public void academy$stopDirectMovement() {
        var phantom = (Phantom) (Object) this;
        moveTargetPoint = phantom.position();
        phantom.stopInPlace();
    }
}
