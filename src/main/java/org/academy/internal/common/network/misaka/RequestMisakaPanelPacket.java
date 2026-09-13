package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.common.world.entity.misaka.MisakaInteractionFeedback;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.server.misaka.MisakaPanelSupport;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.UUID;

@PacketTarget(ThreadType.SERVER)
public final class RequestMisakaPanelPacket
        extends Packet<ServerGamePacketListenerImpl, RequestMisakaPanelPacket> {
    public static final StreamCodec<ByteBuf, RequestMisakaPanelPacket> CODEC = StreamCodec.composite(
            MisakaPacketCodecs.UUID_STREAM_CODEC,
            RequestMisakaPanelPacket::entityUuid,
            RequestMisakaPanelPacket::new
    );
    private static boolean serverInitialized;

    private final UUID entityUuid;

    public RequestMisakaPanelPacket(UUID entityUuid) {
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
    public PacketType<ServerGamePacketListenerImpl, RequestMisakaPanelPacket> getPacketType() {
        return PacketTypes.REQUEST_MISAKA_PANEL.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(RequestMisakaPanelPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var session = MisakaPanelSupport.load(player, packet.entityUuid());
            if (session == null) {
                return;
            }
            if (!session.allow(InteractionGate.Intent.PANEL)) {
                MisakaInteractionFeedback.refuse(session.sister(), session.player());
                return;
            }
            if (!session.inRange(MisakaSisterEntity.PANEL_RANGE_SQR)) {
                return;
            }
            // Panel first; privilege touch deferred — never block UI on index rebuild.
            MisakaPanelSupport.sendPanel(session.player(), session.sister());
            InteractionGate.scheduleAfterInteract(
                    session.level().getServer(),
                    session.record(),
                    session.playerName()
            );
        }
    }
}
