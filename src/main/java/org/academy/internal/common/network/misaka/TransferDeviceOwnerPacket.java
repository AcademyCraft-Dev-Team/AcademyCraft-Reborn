package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.misaka.MisakaDeviceOwnership;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/**
 * Hand a Misaka device's asset ownership to a named online player (design §15.2).
 * The target is carried by name so the confirmed player cannot shift with a list index.
 */
@PacketTarget(ThreadType.SERVER)
public final class TransferDeviceOwnerPacket
        extends Packet<ServerGamePacketListenerImpl, TransferDeviceOwnerPacket> {
    public static final StreamCodec<ByteBuf, TransferDeviceOwnerPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            TransferDeviceOwnerPacket::devicePos,
            ByteBufCodecs.STRING_UTF8,
            TransferDeviceOwnerPacket::targetPlayerName,
            TransferDeviceOwnerPacket::new
    );
    private static boolean serverInitialized;

    private final BlockPos devicePos;
    private final String targetPlayerName;

    public TransferDeviceOwnerPacket(BlockPos devicePos, String targetPlayerName) {
        this.devicePos = devicePos == null ? BlockPos.ZERO : devicePos.immutable();
        this.targetPlayerName = targetPlayerName == null ? "" : targetPlayerName;
    }

    public BlockPos devicePos() {
        return devicePos;
    }

    public String targetPlayerName() {
        return targetPlayerName;
    }

    public static synchronized void initServer() {
        if (serverInitialized) {
            return;
        }
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, TransferDeviceOwnerPacket> getPacketType() {
        return PacketTypes.TRANSFER_DEVICE_OWNER.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(TransferDeviceOwnerPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            var result = MisakaDeviceOwnership.transfer(
                    level,
                    packet.devicePos(),
                    player,
                    packet.targetPlayerName()
            );
            player.sendSystemMessage(
                    Component.translatable(
                            MisakaDeviceOwnership.feedbackKey(result),
                            packet.targetPlayerName()
                    )
            );
        }
    }
}
