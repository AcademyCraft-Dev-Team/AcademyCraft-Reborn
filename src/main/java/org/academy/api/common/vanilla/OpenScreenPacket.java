package org.academy.api.common.vanilla;

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
public class OpenScreenPacket extends Packet<ClientPacketListener, OpenScreenPacket> {
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
    public PacketType<ClientPacketListener, OpenScreenPacket> getPacketType() {
        return PacketTypes.OPEN_SCREEN.get();
    }
}