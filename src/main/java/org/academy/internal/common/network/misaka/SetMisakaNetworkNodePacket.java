package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.misaka.MisakaNetworkPermission;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.server.misaka.MisakaPanelSupport;
import org.academy.internal.server.world.level.storage.MisakaNetworkGovernance;
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
            var session = MisakaPanelSupport.load(packet.getPacketListener().getPlayer(), packet.entityUuid());
            if (session == null) {
                return;
            }
            var player = session.player();
            var level = session.level();
            var sister = session.sister();
            var record = session.record();
            boolean unbound = record.networkNodePos == null;
            boolean unbindRequest = packet.nodeName().isBlank();
            if (unbound && unbindRequest) {
                return;
            }
            var intent = unbound ? InteractionGate.Intent.BIND_FIRST : InteractionGate.Intent.MIGRATE;
            if (!session.allow(intent)) {
                return;
            }
            session.touch();
            if (!session.inRange(MisakaSisterEntity.PANEL_RANGE_SQR)) {
                return;
            }
            var overworld = level.getServer().overworld();
            if (unbindRequest) {
                MisakaNAT.get().unbindSister(level.getServer(), record.misakaUuid);
                MisakaPanelSupport.sendPanel(player, sister);
                return;
            }
            var nodePos = MisakaNAT.get().findNode(overworld, packet.nodeName()).orElse(null);
            if (nodePos == null) {
                player.sendSystemMessage(Component.translatable("message.academy.misaka_node_not_found"));
                MisakaPanelSupport.sendPanel(player, sister);
                return;
            }
            if (!mayAccessTargetNetwork(player, overworld, nodePos)) {
                player.sendSystemMessage(Component.translatable("message.academy.misaka_bind_no_access"));
                MisakaPanelSupport.sendPanel(player, sister);
                return;
            }
            boolean bound = MisakaNAT.get().bindSisterToNode(overworld, record.misakaUuid, nodePos);
            if (!bound) {
                player.sendSystemMessage(Component.translatable("message.academy.misaka_bind_rejected"));
            }
            MisakaPanelSupport.sendPanel(player, sister);
        }

        /**
         * First bind: empty governance → allow (password-era); otherwise require ACCESS.
         * Migrate: require ACCESS on target, or empty admin list fallback.
         */
        private static boolean mayAccessTargetNetwork(
                net.minecraft.server.level.ServerPlayer player,
                ServerLevel overworld,
                BlockPos nodePos
        ) {
            UUID networkId = MisakaNAT.get().resolveNetworkId(overworld, nodePos);
            return MisakaNetworkGovernance.get(overworld.getServer())
                    .hasPermissionOrOpen(player, networkId, MisakaNetworkPermission.ACCESS);
        }
    }
}
