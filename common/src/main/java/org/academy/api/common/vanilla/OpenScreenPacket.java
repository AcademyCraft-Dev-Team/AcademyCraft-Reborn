package org.academy.api.common.vanilla;

import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.ReceiverConstructor;
import org.academy.api.common.network.SenderConstructor;
import org.academy.api.common.network.packet.IPacket;
import org.jetbrains.annotations.NotNull;

@PacketTarget(EnvType.CLIENT)
public class OpenScreenPacket extends IPacket<ClientPacketListener> {
    public String screenName;
    public FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

    @ReceiverConstructor
    public OpenScreenPacket() {
    }

    @SenderConstructor
    public OpenScreenPacket(@NotNull String screenName, @NotNull FriendlyByteBuf buf) {
        this.screenName = screenName;
        this.buf = buf;
    }

    @SenderConstructor
    public OpenScreenPacket(@NotNull String screenName) {
        this.screenName = screenName;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.screenName = buf.readUtf();
        this.buf = new FriendlyByteBuf(buf.readBytes(buf.readableBytes()));
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeUtf(this.screenName);
        buf.writeBytes(this.buf);
    }
}