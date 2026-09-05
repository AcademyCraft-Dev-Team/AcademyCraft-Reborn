package org.academy.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.client.time.TemporalClientRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientLevel.class, priority = 1900)
public abstract class MixinClientLevelTemporalScaling {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void academy$dispatchTemporalPlayerTicks(Entity entity, CallbackInfo ci) {
        if (entity instanceof Player player
                && TemporalClientRuntime.dispatchPlayerTicks(
                        (ClientLevel) (Object) this,
                        player
                )) {
            ci.cancel();
        }
    }
}
