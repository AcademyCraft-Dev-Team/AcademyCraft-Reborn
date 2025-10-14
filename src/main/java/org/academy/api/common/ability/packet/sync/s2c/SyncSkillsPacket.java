package org.academy.api.common.ability.packet.sync.s2c;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.academy.api.common.ability.Skill;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.api.common.network.ThreadType;
import org.academy.internal.common.network.PacketTypes;

import java.util.Set;

@PacketTarget(ThreadType.CLIENT)
public final class SyncSkillsPacket extends Packet<ClientPacketListener, SyncSkillsPacket> {
    public static final StreamCodec<ByteBuf, SyncSkillsPacket> CODEC = StreamCodec.composite(
            Skill.STREAM_CODEC_SET,
            SyncSkillsPacket::getSkills,
            SyncSkillsPacket::new
    );

    private final Set<Skill> skills;

    public SyncSkillsPacket(Set<Skill> skills) {
        this.skills = skills;
    }

    public Set<Skill> getSkills() {
        return skills;
    }

    @Override
    public PacketType<ClientPacketListener, SyncSkillsPacket> getPacketType() {
        return PacketTypes.SYNC_SKILLS.get();
    }
}