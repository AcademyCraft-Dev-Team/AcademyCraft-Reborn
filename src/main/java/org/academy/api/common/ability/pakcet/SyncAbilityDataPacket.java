package org.academy.api.common.ability.pakcet;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.data.AbilityData;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class SyncAbilityDataPacket extends Packet<ClientPacketListener, SyncAbilityDataPacket> {
    public static final StreamCodec<ByteBuf, SyncAbilityDataPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, p -> p.cpData.getMaxCP(),
            ByteBufCodecs.FLOAT, p -> p.cpData.getAvailableCP(),
            ByteBufCodecs.VAR_INT, p -> p.cpData.getLevel().ordinal(),
            ByteBufCodecs.VAR_INT, p -> p.cpData.getStatus().ordinal(),
            ByteBufCodecs.VAR_INT, p -> p.cpData.getStateTimer(),
            ByteBufCodecs.VAR_INT, p -> p.cpData.getCurrSP(),
            ByteBufCodecs.VAR_INT, p -> p.cpData.getMaxSP(),
            ByteBufCodecs.FLOAT, p -> p.cpData.getCurrMP(),
            ByteBufCodecs.FLOAT, p -> p.cpData.getMaxMP(),
            ByteBufCodecs.FLOAT, p -> p.cpData.getAbilityExp(),
            SyncAbilityDataPacket::create
    );

    private final AbilityData cpData;

    public SyncAbilityDataPacket(AbilityData cpData) {
        this.cpData = cpData;
    }

    private static SyncAbilityDataPacket create(float maxCP, float availableCP, int levelOrd, int statusOrd, int stateTimer, int currSP, int maxSP, float currMP, float maxMP, float abilityExp) {
        var data = AbilityData.builder()
                .maxCP(maxCP)
                .availableCP(availableCP)
                .level(AbilityLevel.values()[levelOrd])
                .status(AbilityData.Status.values()[statusOrd])
                .stateTimer(stateTimer)
                .currSP(currSP)
                .maxSP(maxSP)
                .currMP(currMP)
                .maxMP(maxMP)
                .abilityExp(abilityExp)
                .build();
        return new SyncAbilityDataPacket(data);
    }

    public AbilityData getAbilityData() {
        return cpData;
    }

    @Override
    public PacketType<ClientPacketListener, SyncAbilityDataPacket> getPacketType() {
        return PacketTypes.SYNC_ABILITY_DATA.get();
    }
}
