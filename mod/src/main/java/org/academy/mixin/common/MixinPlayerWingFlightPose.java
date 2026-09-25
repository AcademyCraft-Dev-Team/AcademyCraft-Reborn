package org.academy.mixin.common;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.ability.accelerator.skills.WingFlightPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayerWingFlightPose {
    @Inject(method = "getDesiredPose", at = @At("HEAD"), cancellable = true)
    private void academy$useCompactWingFlightPose(CallbackInfoReturnable<Pose> cir) {
        var player = (Player) (Object) this;
        if (WingFlightPose.usesCompactCollision(player)) {
            cir.setReturnValue(Pose.FALL_FLYING);
        }
    }
}
