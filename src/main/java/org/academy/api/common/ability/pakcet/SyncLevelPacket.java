package org.academy.api.common.ability.pakcet;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.api.common.network.ThreadType;
import org.academy.internal.common.network.PacketTypes;

@PacketTarget(ThreadType.CLIENT)
public final class SyncLevelPacket extends Packet<ClientPacketListener, SyncLevelPacket> {
    public static final StreamCodec<ByteBuf, SyncLevelPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            SyncLevelPacket::getLevel,
            SyncLevelPacket::new
    );

    private final int level;

    public SyncLevelPacket(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public PacketType<ClientPacketListener, SyncLevelPacket> getPacketType() {
        return PacketTypes.SYNC_LEVEL.get();
    }
}