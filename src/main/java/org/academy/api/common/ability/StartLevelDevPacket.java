package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.jspecify.annotations.Nullable;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.misaka.api.common.network.packet.PacketType;

public class StartLevelDevPacket extends RequestPacket<ServerGamePacketListenerImpl, StartLevelDevPacket, ClientPacketListener, StartLevelDevPacket.Response> {
    public static final StreamCodec<ByteBuf, StartLevelDevPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.LONG.encode(buf, packet.userPos);
                ByteBufCodecs.VAR_INT.encode(buf, packet.mode.ordinal());
            },
            buf -> new StartLevelDevPacket(
                    ByteBufCodecs.LONG.decode(buf),
                    Mode.byOrdinal(ByteBufCodecs.VAR_INT.decode(buf))
            )
    );

    private final long userPos;
    private final Mode mode;

    public StartLevelDevPacket(long userPos) {
        this(userPos, Mode.DIRECT);
    }

    public StartLevelDevPacket(long userPos, Mode mode) {
        this.userPos = userPos;
        this.mode = mode == null ? Mode.DIRECT : mode;
    }

    public long getUserPos() {
        return userPos;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public PacketType<ClientPacketListener, Response> getResponsePacketType() {
        return PacketTypes.START_LEVEL_DEV_RESPONSE.get();
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, StartLevelDevPacket> getPacketType() {
        return PacketTypes.START_LEVEL_DEV.get();
    }

    public enum Mode {
        DIRECT,
        PREVIEW,
        ACCEPT_PROPS,
        RANDOM;

        private static Mode byOrdinal(int ordinal) {
            var values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DIRECT;
        }
    }

    public static class Response extends ResponsePacket<ClientPacketListener, Response> {
        public static final StreamCodec<ByteBuf, Response> CODEC = StreamCodec.of(
                (buf, response) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, response.status.ordinal());
                    ByteBufCodecs.STRING_UTF8.encode(buf, response.message);
                    var hasRecommendation = response.recommendedCategory != null;
                    ByteBufCodecs.BOOL.encode(buf, hasRecommendation);
                    if (hasRecommendation) ByteBufCodecs.STRING_UTF8.encode(
                            buf, response.recommendedCategory.toString()
                    );
                },
                buf -> {
                    var status = Status.byOrdinal(ByteBufCodecs.VAR_INT.decode(buf));
                    var message = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var recommendation = ByteBufCodecs.BOOL.decode(buf)
                            ? Identifier.tryParse(ByteBufCodecs.STRING_UTF8.decode(buf))
                            : null;
                    return new Response(status, message, recommendation);
                }
        );

        private final Status status;
        private final String message;
        private final @Nullable Identifier recommendedCategory;

        public Response(boolean success, String message) {
            this(success ? Status.STARTED : Status.REJECTED, message, null);
        }

        public Response(Status status, String message, @Nullable Identifier recommendedCategory) {
            this.status = status == null ? Status.REJECTED : status;
            this.message = message;
            this.recommendedCategory = recommendedCategory;
        }

        public boolean isSuccess() {
            return status == Status.STARTED;
        }

        public boolean requiresConfirmation() {
            return status == Status.CONFIRMATION_REQUIRED && recommendedCategory != null;
        }

        public Status getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public @Nullable Identifier getRecommendedCategory() {
            return recommendedCategory;
        }

        @Override
        public PacketType<ClientPacketListener, Response> getPacketType() {
            return PacketTypes.START_LEVEL_DEV_RESPONSE.get();
        }

        public enum Status {
            STARTED,
            CONFIRMATION_REQUIRED,
            REJECTED;

            private static Status byOrdinal(int ordinal) {
                var values = values();
                return ordinal >= 0 && ordinal < values.length ? values[ordinal] : REJECTED;
            }
        }
    }
}
