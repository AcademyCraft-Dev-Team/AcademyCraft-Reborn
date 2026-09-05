package org.academy.mixin.common;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1900)
public abstract class MixinServerGamePacketListenerTemporal {
    private static final String ENSURE_MAIN_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Redirect(
            method = "tickPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;doTick()V"
            )
    )
    private void academy$dispatchTemporalPlayerSimulation(ServerPlayer player) {
        var runtime = academy$runtime(player);
        if (runtime == null) {
            player.doTick();
            return;
        }
        runtime.dispatchPlayerSimulationTicks(player, player::doTick);
    }

    @Inject(
            method = "handleMovePlayer",
            at = @At(
                    value = "INVOKE",
                    target = ENSURE_MAIN_THREAD,
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void academy$rejectMovementDuringTemporalPause(
            ServerboundMovePlayerPacket packet,
            CallbackInfo ci
    ) {
        var listener = (ServerGamePacketListenerImpl) (Object) this;
        var player = listener.player;
        var runtime = academy$runtime(player);
        if (runtime == null || !runtime.isPlayerSimulationPaused(player)) return;
        listener.teleport(
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
        ci.cancel();
    }

    @Inject(
            method = "handlePlayerInput",
            at = @At(
                    value = "INVOKE",
                    target = ENSURE_MAIN_THREAD,
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void academy$rejectInputDuringTemporalPause(
            ServerboundPlayerInputPacket packet,
            CallbackInfo ci
    ) {
        var player = ((ServerGamePacketListenerImpl) (Object) this).player;
        var runtime = academy$runtime(player);
        if (runtime != null && runtime.isPlayerSimulationPaused(player)) {
            ci.cancel();
        }
    }

    private static TemporalRuntime academy$runtime(ServerPlayer player) {
        var context = (MinecraftServerContext) player.level().getServer();
        if (!context.hasAcademyCraftServer()) return null;
        return (TemporalRuntime) context.getAcademyCraftServer()
                .getTemporalService();
    }
}
