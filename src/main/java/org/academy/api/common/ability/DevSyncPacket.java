package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public class DevSyncPacket extends Packet<ClientPacketListener, DevSyncPacket> {
    public static final StreamCodec<ByteBuf, DevSyncPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            DevSyncPacket::getStateOrdinal,
            ByteBufCodecs.FLOAT,
            DevSyncPacket::getProgress,
            ByteBufCodecs.STRING_UTF8,
            DevSyncPacket::getMessage,
            DevSyncPacket::new
    );

    private final int stateOrdinal;
    private final float progress;
    private final String message;

    public DevSyncPacket(int stateOrdinal, float progress, String message) {
        this.stateOrdinal = stateOrdinal;
        this.progress = progress;
        this.message = message;
    }

    public DevSyncPacket(DevState state, float progress, String message) {
        this(state.ordinal(), progress, message);
    }

    public int getStateOrdinal() {
        return stateOrdinal;
    }

    public DevState getState() {
        return DevState.values()[stateOrdinal];
    }

    public float getProgress() {
        return progress;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public PacketType<ClientPacketListener, DevSyncPacket> getPacketType() {
        return PacketTypes.DEV_SYNC.get();
    }
}
