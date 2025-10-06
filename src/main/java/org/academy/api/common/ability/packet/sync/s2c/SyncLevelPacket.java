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
public final class SyncLevelPacket extends Packet<ClientGamePacketListener, SyncLevelPacket> {
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
    public PacketType<ClientGamePacketListener, SyncLevelPacket> getPacketType() {
        return PacketTypes.SYNC_LEVEL.get();
    }
}