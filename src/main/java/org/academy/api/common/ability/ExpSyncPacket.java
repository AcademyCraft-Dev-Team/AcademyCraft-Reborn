package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.common.network.annotation.PacketTarget;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.network.packet.PacketType;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.CLIENT)
public final class ExpSyncPacket extends Packet<ClientGamePacketListener,ExpSyncPacket> {
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
    public @NotNull PacketType<ClientGamePacketListener, ExpSyncPacket> getPacketType() {
        return PacketTypes.EXP_SYNC.get();
    }
}