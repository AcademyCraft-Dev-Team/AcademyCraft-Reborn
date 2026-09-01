package org.academy.mixin.common;

import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    private static final String ENSURE_MAIN_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Inject(
            method = "handleMovePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void academy$rejectImprisonedPlayerMovement(
            ServerboundMovePlayerPacket packet,
            CallbackInfo ci
    ) {
        var listener = (ServerGamePacketListenerImpl) (Object) this;
        var player = listener.player;
        if (PlayerControlSessionManager.validateMovePlayer(player, packet)) {
            ci.cancel();
            return;
        }
        if (EntityMotionGuard.correctImprisonedPlayer(
                player,
                packet.getYRot(player.getYRot()),
                packet.getXRot(player.getXRot())
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void academy$blockControlledInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (academy$blocksUntrustedAction()) ci.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void academy$blockControlledPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (academy$blocksUntrustedAction()) ci.cancel();
    }

    @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void academy$blockControlledUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (academy$blocksUntrustedAction()) ci.cancel();
    }

    @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void academy$blockControlledUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (academy$blocksUntrustedAction()) ci.cancel();
    }

    @Inject(method = "handleSetCarriedItem", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void academy$blockControlledHotbar(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (academy$blocksUntrustedAction()) ci.cancel();
    }

    @Inject(method = "handlePlayerCommand", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void academy$blockControlledPlayerCommand(
            ServerboundPlayerCommandPacket packet,
            CallbackInfo ci
    ) {
        if (academy$blocksUntrustedAction()) ci.cancel();
    }

    @Inject(method = "handlePlayerAbilities", at = @At(value = "INVOKE", target = ENSURE_MAIN_THREAD,
            shift = At.Shift.AFTER), cancellable = true)
    private void academy$blockControlledPlayerAbilities(
            ServerboundPlayerAbilitiesPacket packet,
            CallbackInfo ci
    ) {
        if (academy$blocksUntrustedAction()) ci.cancel();
    }

    private boolean academy$blocksUntrustedAction() {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        return PlayerControlSessionManager.blocksUntrustedWorldAction(player)
                || MentalControlRuntime
                .isFrozen(player);
    }
}
