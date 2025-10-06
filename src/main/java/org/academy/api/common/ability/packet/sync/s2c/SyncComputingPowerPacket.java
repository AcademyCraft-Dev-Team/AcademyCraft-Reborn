package org.academy.api.common.ability.packet.sync.s2c;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.common.network.annotation.PacketTarget;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.network.packet.PacketType;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;

@PacketTarget(ThreadType.CLIENT)
public final class SyncComputingPowerPacket extends Packet<ClientGamePacketListener, SyncComputingPowerPacket> {
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
    public PacketType<ClientGamePacketListener, SyncComputingPowerPacket> getPacketType() {
        return PacketTypes.SYNC_COMPUTING_POWER.get();
    }
}