package org.academy.api.common.ability.pakcet;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.data.CPData;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class SyncCPDataPacket extends Packet<ClientPacketListener, SyncCPDataPacket> {
    public static final StreamCodec<ByteBuf, SyncCPDataPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, p -> p.cpData.getMaxCP(),
            ByteBufCodecs.FLOAT, p -> p.cpData.getAvailableCP(),
            ByteBufCodecs.VAR_INT, p -> p.cpData.getLevel().ordinal(),
            ByteBufCodecs.VAR_INT, p -> p.cpData.getStatus().ordinal(),
            ByteBufCodecs.VAR_INT, p -> p.cpData.getStateTimer(),
            SyncCPDataPacket::create
    );

    private final CPData cpData;

    public SyncCPDataPacket(CPData cpData) {
        this.cpData = cpData;
    }

    private static SyncCPDataPacket create(float maxCP, float availableCP, int levelOrd, int statusOrd, int stateTimer) {
        var data = CPData.builder()
                .maxCP(maxCP)
                .availableCP(availableCP)
                .level(AbilityLevel.values()[levelOrd])
                .status(CPData.Status.values()[statusOrd])
                .stateTimer(stateTimer)
                .build();
        return new SyncCPDataPacket(data);
    }

    public CPData getCPData() {
        return cpData;
    }

    @Override
    public PacketType<ClientPacketListener, SyncCPDataPacket> getPacketType() {
        return PacketTypes.SYNC_COMPUTING_POWER.get();
    }
}