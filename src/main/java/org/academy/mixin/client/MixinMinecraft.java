package org.academy.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.internal.client.ability.VectorReflectionClientRuntime;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.academy.internal.client.ability.mentalout.MentalResistanceClientState;
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.academy.internal.common.network.PlayerLeftClickSwingPacket;
import org.misaka.MisakaNetworkClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow
    @Final
    private Window window;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void academy$blockMentalIntrusionAttack(CallbackInfoReturnable<Boolean> cir) {
        if (MentalIntrusionClientState.blocksWorldInteraction()
                || PlayerControlClientState.blocksWorldInteraction(true)) cir.setReturnValue(false);
    }

    @Inject(
            method = "startAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void academy$sendClickedLeftSwing(CallbackInfoReturnable<Boolean> cir) {
        MisakaNetworkClient.send(PlayerLeftClickSwingPacket.INSTANCE);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void academy$blockMentalIntrusionMining(boolean down, CallbackInfo ci) {
        if (MentalIntrusionClientState.blocksWorldInteraction()
                || PlayerControlClientState.blocksWorldInteraction(down)) ci.cancel();
    }

    @Inject(
            method = "continueAttack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void continueAttack(boolean down, CallbackInfo ci) {
        MisakaNetworkClient.send(PlayerLeftClickSwingPacket.INSTANCE);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void startUseItem(CallbackInfo ci) {
        if (MentalIntrusionClientState.blocksWorldInteraction()
                || PlayerControlClientState.blocksWorldInteraction(true)) ci.cancel();
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void runTick(CallbackInfo info) {
        NeoForge.EVENT_BUS.post(new MainLoopEvent());
        VectorReflectionClientRuntime.tick((Minecraft) (Object) this);
        MentalIntrusionClientState.tick();
        MentalResistanceClientState.tick();
        PlayerControlClientState.tick();
    }

    @Inject(method = "close", at = @At("HEAD"), require = 0)
    private void close(CallbackInfo ci) {
        VectorReflectionClientRuntime.shutdown();
        MentalIntrusionClientState.clearLocal();
        MentalResistanceClientState.clearLocal();
        PlayerControlClientState.clearLocal();
    }

    /**
     * For ResizeDisplayEvent
     */
    @Inject(method = "resizeGui", at = @At("TAIL"))
    private void resize(CallbackInfo ci) {
        var event = new ResizeDisplayEvent(window.getWidth(), window.getHeight());
        NeoForge.EVENT_BUS.post(event);
    }
}
