package org.academy.api.common.ability;

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
public final class ExpSyncPacket extends Packet<ClientPacketListener, ExpSyncPacket> {
    public static final StreamCodec<ByteBuf,ExpSyncPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ExpSyncPacket::getSkillName,
            ByteBufCodecs.FLOAT,
            ExpSyncPacket::getExp,
            ExpSyncPacket::new
    );
    private final String skillName;
    private final float exp;

    public ExpSyncPacket(String skillName, float exp) {
        this.skillName = skillName;
        this.exp = exp;
    }

    public String getSkillName() {
        return skillName;
    }

    public float getExp() {
        return exp;
    }

    @Override
    public PacketType<ClientPacketListener, ExpSyncPacket> getPacketType() {
        return PacketTypes.EXP_SYNC.get();
    }
}