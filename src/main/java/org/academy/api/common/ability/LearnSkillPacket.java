package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.misaka.api.common.network.packet.PacketType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

public class LearnSkillPacket extends RequestPacket<ServerGamePacketListenerImpl, LearnSkillPacket, ClientPacketListener, LearnSkillPacket.Response> {
    public static final StreamCodec<ByteBuf, LearnSkillPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            LearnSkillPacket::getSkillName,
            ByteBufCodecs.LONG,
            LearnSkillPacket::getUserPos,
            LearnSkillPacket::new
    );

    private final String skillName;
    private final long userPos;

    public LearnSkillPacket(String newSkillName, long newUserPos) {
        skillName = newSkillName;
        userPos = newUserPos;
    }

    public String getSkillName() {
        return skillName;
    }

    public long getUserPos() {
        return userPos;
    }

    @Override
    public PacketType<ClientPacketListener, Response> getResponsePacketType() {
        return PacketTypes.LEARN_SKILL_RESPONSE.get();
    }

    @Override
    public @NotNull PacketType<ServerGamePacketListenerImpl, LearnSkillPacket> getPacketType() {
        return PacketTypes.LEARN_SKILL.get();
    }

    public static class Response extends ResponsePacket<ClientPacketListener, Response> {
        public static final StreamCodec<ByteBuf, Response> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                Response::isSuccess,
                Response::new
        );

        private final boolean success;

        public Response(boolean newSuccess) {
            success = newSuccess;
        }

        public boolean isSuccess() {
            return success;
        }

        @Override
        public @NotNull PacketType<ClientPacketListener, Response> getPacketType() {
            return PacketTypes.LEARN_SKILL_RESPONSE.get();
        }
    }
}