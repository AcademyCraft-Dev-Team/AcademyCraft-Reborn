package org.academy.mixin.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {
    protected MixinLocalPlayer(ClientLevel level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/ClientHooks;onMovementInputUpdate(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/player/ClientInput;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void academy$applyAuthorizedPlayerControlInputBeforeActions(CallbackInfo ci) {
        var player = (LocalPlayer) (Object) this;
        PlayerControlClientState.applyAuthorizedInput(player);
        if (PlayerControlClientState.prepareAuthorizedFlight(player)) {
            // An authorized vertical input is not a physical double-tap request. Keeping this at
            // zero prevents jump pulses near the target altitude from toggling flight back off.
            this.jumpTriggerTime = 0;
        }
    }

    @Inject(method = "applyInput", at = @At("HEAD"))
    private void academy$applyAuthorizedPlayerControlInput(CallbackInfo ci) {
        PlayerControlClientState.applyAuthorizedInput((LocalPlayer) (Object) this);
    }
}
