package org.academy.api.common.ability.pakcet;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class SyncMaxComputingPowerPacket extends Packet<ClientPacketListener, SyncMaxComputingPowerPacket> {
    public static final StreamCodec<ByteBuf, SyncMaxComputingPowerPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            SyncMaxComputingPowerPacket::getMaxComputingPower,
            SyncMaxComputingPowerPacket::new
    );

    private final float maxComputingPower;

    public SyncMaxComputingPowerPacket(float maxComputingPower) {
        this.maxComputingPower = maxComputingPower;
    }

    public float getMaxComputingPower() {
        return maxComputingPower;
    }

    @Override
    public PacketType<ClientPacketListener, SyncMaxComputingPowerPacket> getPacketType() {
        return PacketTypes.SYNC_MAX_COMPUTING_POWER.get();
    }
}