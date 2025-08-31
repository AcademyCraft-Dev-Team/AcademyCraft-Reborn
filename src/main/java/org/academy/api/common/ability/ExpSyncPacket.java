package org.academy.api.common.ability;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.CLIENT)
public final class ExpSyncPacket extends Packet<ClientGamePacketListener> {
    public String skillName;
    public float exp;

    public ExpSyncPacket(ClientGamePacketListener packetListener) {
        super(packetListener);
    }

    public ExpSyncPacket(String newSkillName, float newExp) {
        super(null);
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

    @Override
    public @NotNull PacketType<ClientGamePacketListener, ? extends Packet<ClientGamePacketListener>> getPacketType() {
        return PacketTypes.EXP_SYNC.get();
    }
}