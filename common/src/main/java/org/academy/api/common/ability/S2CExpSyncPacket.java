package org.academy.api.common.ability;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.ReceiverConstructor;
import org.academy.api.common.network.SenderConstructor;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.CLIENT)
public class S2CExpSyncPacket extends IPacket<ClientPacketListener> {
    public String skillName;
    public float exp;

    @ReceiverConstructor
    public S2CExpSyncPacket() {
    }

    @SenderConstructor
    public S2CExpSyncPacket(String skillName, float exp) {
        this.skillName = skillName;
        this.exp = exp;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.skillName = buf.readUtf();
        this.exp = buf.readFloat();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeUtf(this.skillName);
        buf.writeFloat(this.exp);
    }
}