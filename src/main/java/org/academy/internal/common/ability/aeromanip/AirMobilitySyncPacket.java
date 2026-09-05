package org.academy.internal.common.ability.aeromanip;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.AirMobility;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class AirMobilitySyncPacket extends Packet<ClientPacketListener, AirMobilitySyncPacket> {
    public static final StreamCodec<ByteBuf, AirMobilitySyncPacket> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeInt(value.mode).writeDouble(value.targetY),
            buf -> new AirMobilitySyncPacket(buf.readInt(), buf.readDouble()));
    private final int mode;
    private final double targetY;

    public AirMobilitySyncPacket(int mode, double targetY) {
        this.mode = mode;
        this.targetY = targetY;
    }

    public void sendTo(ServerPlayer player) {
        MisakaNetworkServer.send(player, this);
    }

    @Override
    public PacketType<ClientPacketListener, AirMobilitySyncPacket> getPacketType() {
        return PacketTypes.AIR_MOBILITY_SYNC.get();
    }

    public static final class Client {
        @SubscribePacket
        public static void receive(AirMobilitySyncPacket packet) {
            var player = Minecraft.getInstance().player;
            if (player != null) AirMobility.setSupport(player, packet.mode, packet.targetY);
        }
    }
}
