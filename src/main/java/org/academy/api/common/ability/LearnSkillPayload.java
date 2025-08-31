package org.academy.api.common.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.network.future.Payload;
import org.academy.api.common.network.future.PayloadType;
import org.academy.api.common.network.future.RequestPayload;
import org.academy.api.common.network.future.ResponsePayload;
import org.academy.internal.common.network.future.PayloadTypes;
import org.jetbrains.annotations.NotNull;

public class LearnSkillPayload extends RequestPayload<ServerGamePacketListenerImpl, LearnSkillPayload.Response> {
    public String skillName;
    public BlockPos userPos;

    public LearnSkillPayload(ServerGamePacketListenerImpl listener) {
        super(listener);
    }

    public LearnSkillPayload(String newSkillName, BlockPos newUserPos) {
        super(null);
        skillName = newSkillName;
        userPos = newUserPos;
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeUtf(skillName);
        buf.writeBlockPos(userPos);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        skillName = buf.readUtf();
        userPos = buf.readBlockPos();
    }

    @Override
    public @NotNull PayloadType<ServerGamePacketListenerImpl, ? extends Payload<ServerGamePacketListenerImpl>> getPayloadType() {
        return PayloadTypes.LEARN_SKILL.get();
    }

    @Override
    public @NotNull PayloadType<?, Response> getExpectedResponsePayloadType() {
        return PayloadTypes.LEARN_SKILL_RESPONSE.get();
    }

    public static class Response extends ResponsePayload<ClientGamePacketListener> {
        public boolean success;

        public Response(ClientGamePacketListener listener) {
            super(listener);
        }

        public Response(boolean newSuccess) {
            success = newSuccess;
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeBoolean(success);
        }

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            success = buf.readBoolean();
        }

        @Override
        public @NotNull PayloadType<ClientGamePacketListener, ? extends Payload<ClientGamePacketListener>> getPayloadType() {
            return PayloadTypes.LEARN_SKILL_RESPONSE.get();
        }
    }
}