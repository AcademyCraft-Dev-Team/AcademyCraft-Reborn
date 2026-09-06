package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.server.misaka.MisakaPanelSupport;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.UUID;

@PacketTarget(ThreadType.SERVER)
public final class SetMisakaNetworkNodePacket
        extends Packet<ServerGamePacketListenerImpl, SetMisakaNetworkNodePacket> {
    public static final StreamCodec<ByteBuf, SetMisakaNetworkNodePacket> CODEC = StreamCodec.composite(
            MisakaPacketCodecs.UUID_STREAM_CODEC,
            SetMisakaNetworkNodePacket::entityUuid,
            ByteBufCodecs.STRING_UTF8,
            SetMisakaNetworkNodePacket::nodeName,
            SetMisakaNetworkNodePacket::new
    );
    private static boolean serverInitialized;

    private final UUID entityUuid;
    private final String nodeName;

    public SetMisakaNetworkNodePacket(UUID entityUuid, String nodeName) {
        this.entityUuid = entityUuid;
        this.nodeName = nodeName;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public String nodeName() {
        return nodeName;
    }

    public static synchronized void initServer() {
        if (serverInitialized) {
            return;
        }
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, SetMisakaNetworkNodePacket> getPacketType() {
        return PacketTypes.SET_MISAKA_NETWORK_NODE.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(SetMisakaNetworkNodePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var level = (ServerLevel) player.level();
            var sister = MisakaPanelSupport.findLoadedEntity(level, packet.entityUuid()).orElse(null);
            if (sister == null) {
                return;
            }
            var record = sister.rosterRecord().orElse(null);
            if (record == null) {
                return;
            }
            String name = player.getGameProfile().name();
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.PANEL)) {
                return;
            }
            InteractionGate.touchBenevolent(record, name, level.getServer());
            if (player.distanceToSqr(sister) > 64.0 * 64.0) {
                return;
            }
            var overworld = level.getServer().overworld();
            if (packet.nodeName().isBlank()) {
                MisakaNAT.get().unbindSister(level.getServer(), record.misakaUuid);
                MisakaPanelSupport.sendPanel(player, sister);
                return;
            }
            var nodePos = MisakaNAT.get().findNode(overworld, packet.nodeName()).orElse(null);
            if (nodePos == null) {
                return;
            }
            MisakaNAT.get().bindSisterToNode(overworld, record.misakaUuid, nodePos);
            MisakaPanelSupport.sendPanel(player, sister);
        }
    }
}
