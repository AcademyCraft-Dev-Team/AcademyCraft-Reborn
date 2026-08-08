package org.academy.mixin.common;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
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
        if (EntityMotionGuard.correctImprisonedPlayer(
                player,
                packet.getYRot(player.getYRot()),
                packet.getXRot(player.getXRot())
        )) {
            ci.cancel();
        }
    }
}
