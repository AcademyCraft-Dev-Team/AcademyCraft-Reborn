package org.academy.api.common.ability;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.CLIENT)
public class ExpSyncPacket extends IPacket<ClientPacketListener> {
    public String skillName;
    public float exp;

    public ExpSyncPacket() {
    }

    public ExpSyncPacket(String newSkillName, float newExp) {
        skillName = newSkillName;
        exp = newExp;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        skillName = buf.readUtf();
        exp = buf.readFloat();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeUtf(skillName);
        buf.writeFloat(exp);
    }
}