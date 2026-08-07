package org.academy.internal.common.ability.accelerator.reflection.compat;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.client.ability.VectorReflectionClientRuntime;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class VectorDefenseFeedbackPacket
        extends Packet<ClientPacketListener, VectorDefenseFeedbackPacket> {
    public static final StreamCodec<ByteBuf, VectorDefenseFeedbackPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            packet -> packet.entityId,
            ByteBufCodecs.LONG,
            packet -> packet.serverTick,
            VectorDefenseFeedbackPacket::new
    );
    private static boolean clientInitialized;
    private final int entityId;
    private final long serverTick;

    public VectorDefenseFeedbackPacket(int entityId, long serverTick) {
        this.entityId = entityId;
        this.serverTick = serverTick;
    }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static void broadcast(ServerPlayer defender, long serverTick) {
        if (!(defender.level() instanceof ServerLevel level)) return;
        var packet = new VectorDefenseFeedbackPacket(defender.getId(), serverTick);
        for (var observer : level.players()) {
            if (observer.distanceToSqr(defender) <= 128.0 * 128.0) {
                MisakaNetworkServer.send(observer, packet);
            }
        }
    }

    @Override
    public PacketType<ClientPacketListener, VectorDefenseFeedbackPacket> getPacketType() {
        return PacketTypes.VECTOR_DEFENSE_FEEDBACK.get();
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(VectorDefenseFeedbackPacket packet) {
            VectorReflectionClientRuntime.confirmFullDefense(packet.entityId, packet.serverTick);
        }
    }
}
