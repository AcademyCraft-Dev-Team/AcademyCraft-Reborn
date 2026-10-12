package org.academy.mixin.common;

import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.packet.C2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep structure use behind earlier vanilla hotbar, movement and player-command packets. */
@Mixin(C2SPacket.class)
public abstract class MixinC2SPacketNativeStructureOrder {
    @Shadow @Final private int id;

    @Inject(method = "handle(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;)V", at = @At("HEAD"))
    private void academy$orderNativeStructureUse(ServerGamePacketListenerImpl listener, CallbackInfo callback) {
        if (id == PacketTypes.NATIVE_STRUCTURE_USE.get().getPacketId()
                || id == PacketTypes.NATIVE_STRUCTURE_MINE.get().getPacketId()) {
            // In 26.2 vanilla uses PacketProcessor, while the wrapper uses server.execute.
            // Route this wrapper into the same ordered queue before its normal decode/dispatch.
            PacketUtils.ensureRunningOnSameThread((C2SPacket) (Object) this, listener, listener.player.level());
        }
    }
}
