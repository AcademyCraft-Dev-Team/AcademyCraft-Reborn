package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
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

/** Privilege player sets the sister's manual wander anchor to the player's current chunk. */
@PacketTarget(ThreadType.SERVER)
public final class SetMisakaWanderAnchorPacket
        extends Packet<ServerGamePacketListenerImpl, SetMisakaWanderAnchorPacket> {
    public static final StreamCodec<ByteBuf, SetMisakaWanderAnchorPacket> CODEC = StreamCodec.composite(
            MisakaPacketCodecs.UUID_STREAM_CODEC,
            SetMisakaWanderAnchorPacket::entityUuid,
            SetMisakaWanderAnchorPacket::new
    );
    private static boolean serverInitialized;

    private final UUID entityUuid;

    public SetMisakaWanderAnchorPacket(UUID entityUuid) {
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
    public PacketType<ServerGamePacketListenerImpl, SetMisakaWanderAnchorPacket> getPacketType() {
        return PacketTypes.SET_MISAKA_WANDER_ANCHOR.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(SetMisakaWanderAnchorPacket packet) {
            var session = MisakaPanelSupport.load(packet.getPacketListener().getPlayer(), packet.entityUuid());
            if (session == null) {
                return;
            }
            if (!session.allow(InteractionGate.Intent.STATE)) {
                return;
            }
            session.touch();
            if (!session.inRange(MisakaSisterEntity.PANEL_RANGE_SQR)) {
                return;
            }
            ChunkPos anchor = ChunkPos.containing(session.player().blockPosition());
            MisakaSisterRoster.get(session.level().getServer()).modify(session.record().misakaUuid, sisterRecord -> {
                sisterRecord.wanderAnchorChunk = anchor;
            });
            MisakaPanelSupport.sendPanel(session.player(), session.sister());
        }
    }
}
