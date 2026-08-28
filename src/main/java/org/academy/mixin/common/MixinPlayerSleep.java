package org.academy.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.level.block.AbilityDeveloperSleep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayerSleep {
    @Inject(method = "isSleepingLongEnough", at = @At("HEAD"), cancellable = true)
    private void academy$onlySkipTimeAtNight(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player
                && AbilityDeveloperSleep.isSleepingAtDeveloper(player)
                && !AbilityDeveloperSleep.canSkipTime(player)) {
            cir.setReturnValue(false);
        }
    }
}
