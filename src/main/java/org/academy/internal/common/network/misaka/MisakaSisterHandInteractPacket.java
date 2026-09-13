package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.MisakaSisterRosterSync;
import org.academy.internal.server.misaka.MisakaPanelSupport;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.UUID;

/**
 * Client → server fallback when vanilla EntityInteract misses / client withoutItem never
 * reaches the server (tower recover / feed / pet).
 */
@PacketTarget(ThreadType.SERVER)
public final class MisakaSisterHandInteractPacket
        extends Packet<ServerGamePacketListenerImpl, MisakaSisterHandInteractPacket> {
    public static final StreamCodec<ByteBuf, MisakaSisterHandInteractPacket> CODEC = StreamCodec.composite(
            MisakaPacketCodecs.UUID_STREAM_CODEC,
            MisakaSisterHandInteractPacket::entityUuid,
            ByteBufCodecs.VAR_INT,
            packet -> packet.hand().ordinal(),
            (entityUuid, handOrdinal) -> new MisakaSisterHandInteractPacket(
                    entityUuid,
                    handOrdinal == InteractionHand.OFF_HAND.ordinal()
                            ? InteractionHand.OFF_HAND
                            : InteractionHand.MAIN_HAND
            )
    );
    private static boolean serverInitialized;

    private final UUID entityUuid;
    private final InteractionHand hand;

    public MisakaSisterHandInteractPacket(UUID entityUuid, InteractionHand hand) {
        this.entityUuid = entityUuid;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public InteractionHand hand() {
        return hand;
    }

    public static synchronized void initServer() {
        if (serverInitialized) {
            return;
        }
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, MisakaSisterHandInteractPacket> getPacketType() {
        return PacketTypes.MISAKA_SISTER_HAND_INTERACT.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(MisakaSisterHandInteractPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            var sister = MisakaPanelSupport.findLoadedEntity(level, packet.entityUuid()).orElse(null);
            if (sister == null) {
                sister = MisakaPanelSupport.findLoadedEntityAnyDimension(
                        level.getServer(), packet.entityUuid()).orElse(null);
            }
            if (sister == null) {
                return;
            }
            double range = Math.max(player.entityInteractionRange() + 1.0, MisakaSisterEntity.PICKUP_RANGE);
            if (player.distanceToSqr(sister) > range * range) {
                return;
            }
            // Do not require a pre-resolved roster session — ensureRegistered then interact.
            MisakaSisterRosterSync.ensureRegistered(sister);
            sister.forceMobInteract(player, packet.hand());
        }
    }
}
