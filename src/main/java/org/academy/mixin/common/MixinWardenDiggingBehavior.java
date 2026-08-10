package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.warden.Digging;
import net.minecraft.world.entity.monster.warden.Warden;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the Warden digging behavior from discarding a currently controlled subject.
 */
@Mixin(Digging.class)
public abstract class MixinWardenDiggingBehavior {
    @Inject(
            method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;J)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$keepControlledWardenInWorld(
            ServerLevel level,
            Warden warden,
            long gameTime,
            CallbackInfo ci
    ) {
        if (!MentalControlRuntime.hasActiveControl(warden)) return;
        warden.setPose(Pose.STANDING);
        ci.cancel();
    }
}
