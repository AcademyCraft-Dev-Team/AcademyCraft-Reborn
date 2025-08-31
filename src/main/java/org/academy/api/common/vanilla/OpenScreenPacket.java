package org.academy.api.common.vanilla;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.CLIENT)
public class OpenScreenPacket extends Packet<ClientGamePacketListener> {
    public String screenName;
    private FriendlyByteBuf dataPayload;

    public OpenScreenPacket(ClientGamePacketListener listener) {
        super(listener);
        dataPayload = new FriendlyByteBuf(Unpooled.buffer());
    }

    public OpenScreenPacket(@NotNull String newScreenName, @NotNull FriendlyByteBuf payload) {
        super(null);
        screenName = newScreenName;
        dataPayload = new FriendlyByteBuf(Unpooled.buffer(payload.readableBytes()));
        payload.readBytes(dataPayload, payload.readableBytes());
    }

    @SuppressWarnings("unused")
    public OpenScreenPacket(@NotNull String newScreenName) {
        super(null);
        screenName = newScreenName;
        dataPayload = new FriendlyByteBuf(Unpooled.buffer());
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        screenName = buf.readUtf();
        var readableBytes = buf.readableBytes();
        dataPayload = new FriendlyByteBuf(buf.readBytes(readableBytes));
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeUtf(screenName);
        if (dataPayload != null && dataPayload.readableBytes() > 0) {
            buf.writeBytes(dataPayload.copy());
        }
    }

    public FriendlyByteBuf getDataPayload() {
        return new FriendlyByteBuf(dataPayload.copy());
    }

    @Override
    public @NotNull PacketType<ClientGamePacketListener, ? extends Packet<ClientGamePacketListener>> getPacketType() {
        return PacketTypes.OPEN_SCREEN.get();
    }
}