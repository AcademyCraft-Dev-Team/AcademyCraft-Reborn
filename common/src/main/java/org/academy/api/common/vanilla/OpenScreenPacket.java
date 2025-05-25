package org.academy.api.common.vanilla;

import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.IPacket;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.CLIENT)
public class OpenScreenPacket extends IPacket<ClientPacketListener> {
    public String screenName;
    private FriendlyByteBuf dataPayload;

    public OpenScreenPacket() {
        this.dataPayload = new FriendlyByteBuf(Unpooled.buffer());
    }

    public OpenScreenPacket(@NotNull String screenName, @NotNull FriendlyByteBuf payload) {
        this.screenName = screenName;
        this.dataPayload = new FriendlyByteBuf(Unpooled.buffer(payload.readableBytes()));
        payload.readBytes(this.dataPayload, payload.readableBytes());
    }

    @SuppressWarnings("unused")
    public OpenScreenPacket(@NotNull String screenName) {
        this.screenName = screenName;
        this.dataPayload = new FriendlyByteBuf(Unpooled.buffer());
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.screenName = buf.readUtf();
        int readableBytes = buf.readableBytes();
        this.dataPayload = new FriendlyByteBuf(buf.readBytes(readableBytes));
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeUtf(this.screenName);
        if (this.dataPayload != null && this.dataPayload.readableBytes() > 0) {
            buf.writeBytes(this.dataPayload.copy());
        }
    }

    public FriendlyByteBuf getDataPayload() {
        return new FriendlyByteBuf(dataPayload.copy());
    }
}