package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonStrafePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import org.academy.api.common.entitycontrol.AttackDecision;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DragonStrafePlayerPhase.class)
public abstract class MixinDragonStrafePlayerPhase extends AbstractDragonPhaseInstance {
    @Shadow
    private @Nullable LivingEntity attackTarget;

    protected MixinDragonStrafePlayerPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Inject(method = "doServerTick", at = @At("HEAD"), cancellable = true)
    private void academy$stopMentalControlAllyStrafe(ServerLevel level, CallbackInfo ci) {
        if (attackTarget == null
                || MentalControlRuntime.attackDecision(dragon, attackTarget) != AttackDecision.DENY) {
            return;
        }
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        ci.cancel();
    }
}
