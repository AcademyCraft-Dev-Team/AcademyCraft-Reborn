package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.common.world.entity.misaka.MisakaInteractionFeedback;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.server.misaka.MisakaPanelSupport;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.UUID;

@PacketTarget(ThreadType.SERVER)
public final class TogglePickUpMisakaPacket
        extends Packet<ServerGamePacketListenerImpl, TogglePickUpMisakaPacket> {
    public static final StreamCodec<ByteBuf, TogglePickUpMisakaPacket> CODEC = StreamCodec.composite(
            MisakaPacketCodecs.UUID_STREAM_CODEC,
            TogglePickUpMisakaPacket::entityUuid,
            TogglePickUpMisakaPacket::new
    );
    private static boolean serverInitialized;

    private final UUID entityUuid;

    public TogglePickUpMisakaPacket(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public static synchronized void initServer() {
        if (serverInitialized) {
            return;
        }
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, TogglePickUpMisakaPacket> getPacketType() {
        return PacketTypes.TOGGLE_PICK_UP_MISAKA.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePickUpMisakaPacket packet) {
            var session = MisakaPanelSupport.load(packet.getPacketListener().getPlayer(), packet.entityUuid());
            if (session == null || !session.player().isAlive()) {
                return;
            }
            if (!session.inRange(MisakaSisterEntity.PICKUP_RANGE_SQR)) {
                return;
            }
            if (!session.allow(InteractionGate.Intent.PICKUP)) {
                MisakaInteractionFeedback.pickupDenied(session.player());
                return;
            }
            session.touch();
            MisakaSisterRoster.get(session.level().getServer()).setDirty();
            togglePickup(session.player(), session.sister());
        }

        private static void togglePickup(ServerPlayer player, MisakaSisterEntity sister) {
            if (sister.isPassenger() && sister.getVehicle() == player) {
                sister.stopRiding();
                syncPassengers(player);
                return;
            }
            if (!sister.startRiding(player, true, true)) {
                MisakaInteractionFeedback.pickupDenied(player);
                return;
            }
            syncPassengers(player);
        }

        /**
         * Vanilla only self-sends passenger packets when the player mounts something.
         * When a mob mounts the player, tracking updates skip the ridden player, so clients never see the pickup.
         */
        private static void syncPassengers(ServerPlayer player) {
            var packet = new ClientboundSetPassengersPacket(player);
            // Vanilla never self-sends this when a mob mounts the player.
            player.connection.send(packet);
            var level = (ServerLevel) player.level();
            for (ServerPlayer viewer : level.players()) {
                if (viewer != player && viewer.distanceToSqr(player) <= 64.0 * 64.0) {
                    viewer.connection.send(packet);
                }
            }
        }
    }
}
