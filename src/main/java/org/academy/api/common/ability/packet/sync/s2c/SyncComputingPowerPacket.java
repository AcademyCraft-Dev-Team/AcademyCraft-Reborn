package org.academy.api.common.ability.packet.sync.s2c;

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
public final class SyncComputingPowerPacket extends Packet<ClientPacketListener, SyncComputingPowerPacket> {
    public static final StreamCodec<ByteBuf, SyncComputingPowerPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            SyncComputingPowerPacket::getComputingPower,
            SyncComputingPowerPacket::new
    );

    private final float computingPower;

    public SyncComputingPowerPacket(float computingPower) {
        this.computingPower = computingPower;
    }

    public float getComputingPower() {
        return computingPower;
    }

    @Override
    public PacketType<ClientPacketListener, SyncComputingPowerPacket> getPacketType() {
        return PacketTypes.SYNC_COMPUTING_POWER.get();
    }
}