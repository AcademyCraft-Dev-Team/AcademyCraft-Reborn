package org.academy.mixin.common;

import net.minecraft.world.entity.boss.enderdragon.phases.DragonHoverPhase;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.mentalout.control.DragonHoverPhaseAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DragonHoverPhase.class)
public abstract class MixinDragonHoverPhase implements DragonHoverPhaseAccess {
    @Shadow
    private Vec3 targetLocation;

    @Override
    public void academy$setMentalControlFlightTarget(Vec3 target) {
        targetLocation = target;
    }
}
