package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents special Brain-based mobs from reacquiring autonomous targets during exclusive control.
 */
@Mixin(Brain.class)
public abstract class MixinBrain<E extends LivingEntity> {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void academy$suppressAutonomousMentalControlBrain(
            ServerLevel level,
            E owner,
            CallbackInfo ci
    ) {
        if (owner instanceof Mob mob && MentalControlRuntime.suppressesAutonomousBrain(mob)) {
            ci.cancel();
        }
    }
}
