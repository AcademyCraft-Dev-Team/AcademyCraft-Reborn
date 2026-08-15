package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.misaka.api.common.network.packet.PacketType;

public class StartSkillDevPacket extends RequestPacket<ServerGamePacketListenerImpl, StartSkillDevPacket, ClientPacketListener, StartSkillDevPacket.Response> {
    public static final StreamCodec<ByteBuf, StartSkillDevPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            StartSkillDevPacket::getSkillName,
            DevelopmentSource.CODEC,
            StartSkillDevPacket::getSource,
            StartSkillDevPacket::new
    );

    private final String skillName;
    private final DevelopmentSource source;

    public StartSkillDevPacket(String skillName, long userPos) {
        this(skillName, DevelopmentSource.block(BlockPos.of(userPos)));
    }

    public StartSkillDevPacket(String skillName, InteractionHand hand) {
        this(skillName, DevelopmentSource.tablet(hand));
    }

    public StartSkillDevPacket(String skillName, DevelopmentSource source) {
        this.skillName = skillName;
        this.source = source;
    }

    public String getSkillName() {
        return skillName;
    }

    public long getUserPos() {
        return source.blockPos() == null ? 0L : source.blockPos().asLong();
    }

    public DevelopmentSource getSource() {
        return source;
    }

    @Override
    public PacketType<ClientPacketListener, Response> getResponsePacketType() {
        return PacketTypes.START_SKILL_DEV_RESPONSE.get();
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, StartSkillDevPacket> getPacketType() {
        return PacketTypes.START_SKILL_DEV.get();
    }

    public static class Response extends ResponsePacket<ClientPacketListener, Response> {
        public static final StreamCodec<ByteBuf, Response> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                Response::isSuccess,
                ByteBufCodecs.STRING_UTF8,
                Response::getMessage,
                Response::new
        );

        private final boolean success;
        private final String message;

        public Response(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public PacketType<ClientPacketListener, Response> getPacketType() {
            return PacketTypes.START_SKILL_DEV_RESPONSE.get();
        }
    }
}
