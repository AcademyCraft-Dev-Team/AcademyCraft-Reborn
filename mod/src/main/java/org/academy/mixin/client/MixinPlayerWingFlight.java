package org.academy.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.ability.WingFlightClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class MixinPlayerWingFlight {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void academy$travelWithWings(Vec3 input, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && WingFlightClient.travel(player)) ci.cancel();
    }
}
