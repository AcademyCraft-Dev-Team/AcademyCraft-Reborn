package org.academy.api.common.vanilla;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.common.network.annotation.PacketTarget;
import org.academy.api.common.network.packet.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.internal.common.network.PacketTypes;

@PacketTarget(ThreadType.CLIENT)
public class OpenScreenPacket extends Packet<ClientGamePacketListener, OpenScreenPacket> {
    public static final StreamCodec<ByteBuf, OpenScreenPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            OpenScreenPacket::getScreenName,
            ByteBufCodecs.BYTE_ARRAY,
            OpenScreenPacket::getDataPayload,
            OpenScreenPacket::new
    );

    private final String screenName;
    private final byte[] dataPayload;

    public OpenScreenPacket(String screenName, byte[] dataPayload) {
        this.screenName = screenName;
        this.dataPayload = dataPayload;
    }

    public OpenScreenPacket(String screenName) {
        this(screenName, new byte[0]);
    }

    public String getScreenName() {
        return screenName;
    }

    public byte[] getDataPayload() {
        return dataPayload;
    }

    @Override
    public PacketType<ClientGamePacketListener, OpenScreenPacket> getPacketType() {
        return PacketTypes.OPEN_SCREEN.get();
    }
}