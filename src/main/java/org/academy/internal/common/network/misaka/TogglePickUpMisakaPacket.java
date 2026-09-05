package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
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
    private static final double PICKUP_RANGE = 4.0;
    private static final double PICKUP_RANGE_SQR = PICKUP_RANGE * PICKUP_RANGE;

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
            var player = packet.getPacketListener().getPlayer();
            var sister = MisakaPanelSupport.findLoadedEntity((ServerLevel) player.level(), packet.entityUuid()).orElse(null);
            if (sister == null || !player.isAlive()) {
                return;
            }
            if (player.distanceToSqr(sister) > PICKUP_RANGE_SQR) {
                return;
            }
            var record = sister.rosterRecord().orElse(null);
            if (record == null) {
                return;
            }
            String name = player.getGameProfile().name();
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.PICKUP)) {
                MisakaInteractionFeedback.pickupDenied(player);
                return;
            }
            InteractionGate.touchBenevolent(record, name, ((ServerLevel) player.level()).getServer());
            MisakaSisterRoster.get(player.level().getServer()).setDirty();
            togglePickup(player, sister);
        }

        private static void togglePickup(ServerPlayer player, MisakaSisterEntity sister) {
            if (sister.isPassenger() && sister.getVehicle() == player) {
                sister.stopRiding();
                return;
            }
            sister.startRiding(player, true, true);
        }
    }
}
